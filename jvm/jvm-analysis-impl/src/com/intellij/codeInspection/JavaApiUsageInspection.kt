// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.codeInspection.options.OptDropdown
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.*
import com.intellij.codeInspection.options.OptionController
import com.intellij.java.JavaBundle
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.JdkApiCompatabilityCache
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaVersionService
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.uast.UastVisitorAdapter
import com.siyeh.ig.callMatcher.CallMatcher
import org.jdom.Element
import org.jetbrains.uast.*

private val logger = logger<JavaApiUsageInspection>()

private const val EFFECTIVE_LL = "effectiveLL"

/**
 * In order to add the support for new API in the most recent JDK execute:
 * <ol>
 *   <li>Generate apiXXX.txt by running [com.intellij.codeInspection.tests.JavaApiUsageGenerator#testCollectSinceApiUsages]</li>
 *   <li>Put the generated text file under community/java/java-analysis-api/src/com/intellij/openapi/module</li>
 *   <li>Add two new entries to the {@link com.intellij.openapi.module.LanguageLevelUtil.ourPresentableShortMessage}:
 *    <ul>
 *      <li>The First entry: The key is the most recent language level, the value is the second to the most recent language level.</li>
 *      <li>The Second entry: The key is the most recent preview language level, the value is the second to the most recent language level.</li>
 *    </ul>
 *   </li>
 * </ol>
 */
class JavaApiUsageInspection : AbstractBaseUastLocalInspectionTool() {
  override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.ERROR

  private var effectiveLanguageLevel: LanguageLevel? = null

  override fun getOptionsPane(): OptPane {
    var levels: List<OptDropdown.Option> = LanguageLevel.entries.map { option(it.name, it.presentableText) }
    levels = listOf(option("null", JavaBundle.message("label.forbid.api.usages.project"))) + levels
    return pane(
      dropdown("effectiveLanguageLevel", JavaBundle.message("label.forbid.api.usages"), *levels.toTypedArray())
    )
  }

  override fun getOptionController(): OptionController {
    return super.getOptionController().onValue(
      "effectiveLanguageLevel",
      { effectiveLanguageLevel?.name ?: "null" },
      { value -> effectiveLanguageLevel = if (value == "null") null else LanguageLevel.valueOf(value) }
    )
  }

  override fun readSettings(node: Element) {
    val element = node.getChild(EFFECTIVE_LL)
    if (element != null) {
      effectiveLanguageLevel = element.getAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)?.let {
        LanguageLevel.valueOf(it)
      }
    }
  }

  override fun writeSettings(node: Element) {
    if (effectiveLanguageLevel != null) {
      val llElement = Element(EFFECTIVE_LL)
      llElement.setAttribute(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, effectiveLanguageLevel.toString())
      node.addContent(llElement)
    }
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastVisitorAdapter(JavaApiUsageVisitor(JavaApiUsageProcessor(isOnTheFly, holder), holder, isOnTheFly), true)

  inner class JavaApiUsageVisitor(
    apiUsageProcessor: ApiUsageProcessor,
    private val holder: ProblemsHolder,
    private val isOnTheFly: Boolean
  ) : ApiUsageUastVisitor(apiUsageProcessor) {
    private inline val defaultMethods get() = CallMatcher
      .exactInstanceCall(CommonClassNames.JAVA_UTIL_ITERATOR, "remove")
      .parameterCount(0)

    private inline val overrideModifierLanguages get() = listOf("kotlin", "scala")

    override fun visitClass(node: UClass): Boolean {
      val javaPsi = node.javaPsi
      if (!javaPsi.hasModifierProperty(PsiModifier.ABSTRACT) && javaPsi !is PsiTypeParameter) { // Don't go into classes (anonymous, locals).
        val module = ModuleUtilCore.findModuleForPsiElement(javaPsi) ?: return true
        val effectiveLanguageLevel = getEffectiveLanguageLevel(module)
        if (!effectiveLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
          val version = JavaVersionService.getInstance().getJavaSdkVersion(javaPsi)
          if (version != null && version.isAtLeast(JavaSdkVersion.JDK_1_8)) {
            val signatures = javaPsi.visibleSignatures.filter { signature -> defaultMethods.methodMatches(signature.method) }
            if (signatures.isNotEmpty()) {
              val jdkName = effectiveLanguageLevel.shortText
              val message = if (signatures.size == 1) {
                JvmAnalysisBundle.message("jvm.inspections.1.8.problem.single.descriptor", signatures.first().name, jdkName)
              }
              else {
                JvmAnalysisBundle.message("jvm.inspections.1.8.problem.descriptor", signatures.size, jdkName)
              }
              holder.registerUProblem(node, message, QuickFixFactory.getInstance().createImplementMethodsFix(javaPsi))
            }
          }
        }
      }
      return true
    }

    override fun visitMethod(node: UMethod): Boolean {
      if (node.isConstructor) {
        checkImplicitCallOfSuperEmptyConstructor(node)
      }
      else {
        processMethodOverriding(node, node.javaPsi.findSuperMethods(true))
      }
      return true
    }

    private fun processMethodOverriding(method: UMethod, overriddenMethods: Array<PsiMethod>) {
      val overrideAnnotation = method.findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE)
      val hasOverrideModifier = overrideModifierLanguages.any { method.sourcePsi?.language != Language.findLanguageByID(it) }
      if (overrideAnnotation == null && !hasOverrideModifier) return
      val sourcePsi = method.sourcePsi ?: return
      val module = ModuleUtilCore.findModuleForPsiElement(sourcePsi) ?: return
      val languageLevel = getEffectiveLanguageLevel(module)
      val lastIncompatibleLevel = overriddenMethods.mapNotNull { overriddenMethod ->
        JdkApiCompatabilityCache.getInstance().firstCompatibleLanguageLevel(overriddenMethod, languageLevel)
      }.minOrNull() ?: return
      val toHighlight = overrideAnnotation?.uastAnchor?.sourcePsi ?: method.uastAnchor?.sourcePsi ?: return
      if (shouldReportSinceLevelForElement(lastIncompatibleLevel, sourcePsi) == true) return
      registerError(toHighlight, lastIncompatibleLevel, holder, isOnTheFly)
    }
  }

  inner class JavaApiUsageProcessor(private val isOnTheFly: Boolean, private val holder: ProblemsHolder) : ApiUsageProcessor {

    private inline val ignored6ClassesApi get() = setOf("java.awt.geom.GeneralPath")
    private inline val generifiedClasses get() = setOf("javax.swing.JComboBox", "javax.swing.ListModel", "javax.swing.JList")

    override fun processConstructorInvocation(
      sourceNode: UElement, instantiatedClass: PsiClass, constructor: PsiMethod?, subclassDeclaration: UClass?,
    ) {
      constructor ?: return
      val sourcePsi = sourceNode.sourcePsi ?: return
      val module = ModuleUtilCore.findModuleForPsiElement(sourcePsi) ?: return
      val languageLevel = getEffectiveLanguageLevel(module)
      val lastIncompatibleLevel = JdkApiCompatabilityCache.getInstance().firstCompatibleLanguageLevel(constructor, languageLevel)
                                  ?: return
      if (shouldReportSinceLevelForElement(lastIncompatibleLevel, sourcePsi) == true) return
      registerError(sourcePsi, lastIncompatibleLevel, holder, isOnTheFly)
    }

    override fun processReference(sourceNode: UElement, target: PsiModifierListOwner, qualifier: UExpression?) {
      val sourcePsi = sourceNode.sourcePsi ?: return
      if (target !is PsiMember) return
      var languageLevel: LanguageLevel? = null
      val module = ModuleUtilCore.findModuleForPsiElement(sourcePsi)
      if (module != null) {
        languageLevel = getEffectiveLanguageLevel(module)
        if (languageLevel.isUnsupported) {
          languageLevel = languageLevel.getNonPreviewLevel()
        }
      }
      else if (sourcePsi.containingFile.virtualFile is LightVirtualFile) {
        //it is necessary for generated files (for example, check completions)
        languageLevel = sourcePsi.containingFile.getUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY)
      }
      if (languageLevel == null) return
      val lastIncompatibleLevel = JdkApiCompatabilityCache.getInstance().firstCompatibleLanguageLevel(target, languageLevel)
      if (lastIncompatibleLevel != null) {
        if (shouldReportSinceLevelForElement(lastIncompatibleLevel, sourcePsi) == true) return
        val psiClass = if (qualifier != null) {
          PsiUtil.resolveClassInType(qualifier.getExpressionType())
        }
        else {
          sourceNode.getContainingUClass()?.javaPsi
        }
        if (psiClass != null) {
          if (isIgnored(psiClass)) return
          for (superClass in psiClass.supers) {
            if (isIgnored(superClass)) return
          }
        }
        registerError(sourcePsi, lastIncompatibleLevel, holder, isOnTheFly)
      }
      else if (target is PsiClass && !languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
        for (generifiedClass in generifiedClasses) {
          if (InheritanceUtil.isInheritor(target, generifiedClass) && !isRawInheritance(generifiedClass, target, mutableSetOf())) {
            val message = JvmAnalysisBundle.message(
              "jvm.inspections.1.7.problem.descriptor",
              languageLevel.toJavaVersion().feature
            )
            holder.registerProblem(sourcePsi, message)
            break
          }
        }
      }
    }

    private fun isRawInheritance(generifiedClassQName: String, currentClass: PsiClass, visited: MutableSet<in PsiClass>): Boolean {
      return currentClass.superTypes.any { classType ->
        if (classType.isRaw) return true
        val resolveResult = classType.resolveGenerics()
        val superClass = resolveResult.element ?: return@any false
        visited.add(superClass) &&
        InheritanceUtil.isInheritor(superClass, generifiedClassQName) &&
        isRawInheritance(generifiedClassQName, superClass, visited)
      }
    }

    private fun isIgnored(psiClass: PsiClass): Boolean {
      val qualifiedName = psiClass.qualifiedName
      return qualifiedName != null && ignored6ClassesApi.contains(qualifiedName)
    }
  }

  /** Only runs in production because tests have incorrect SDKs when no mock SDK is available. */
  private fun shouldReportSinceLevelForElement(lastIncompatibleLevel: LanguageLevel, context: PsiElement): Boolean? {
    val jdkVersion = JavaVersionService.getInstance().getJavaSdkVersion(context) ?: return null
    return lastIncompatibleLevel.isAtLeast(jdkVersion.maxLanguageLevel) && !ApplicationManager.getApplication().isUnitTestMode
  }

  private fun registerError(reference: PsiElement, sinceLanguageLevel: LanguageLevel, holder: ProblemsHolder, isOnTheFly: Boolean) {
    val targetLanguageLevel = LanguageLevelUtil.getNextLanguageLevel(sinceLanguageLevel) ?: run {
      logger.error("Unable to get the next language level for $sinceLanguageLevel")
      return
    }
    if (reference.getUastParentOfType<UComment>() != null) return
    val message = JvmAnalysisBundle.message(
      "jvm.inspections.1.5.problem.descriptor", sinceLanguageLevel.toJavaVersion().toFeatureString()
    )
    val fix = if (isOnTheFly) {
      QuickFixFactory.getInstance().createIncreaseLanguageLevelFix(targetLanguageLevel) as LocalQuickFix
    }
    else null
    holder.registerProblem(reference, message, *LocalQuickFix.notNullElements(fix))
  }

  private fun getEffectiveLanguageLevel(module: Module): LanguageLevel {
    return effectiveLanguageLevel ?: LanguageLevelUtil.getEffectiveLanguageLevel(module)
  }
}