// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaapi

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.java.JavaBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaVersionService
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.uast.UastVisitorAdapter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jdom.Element
import org.jetbrains.uast.*
import java.awt.BorderLayout
import java.awt.event.ActionListener
import javax.swing.*

/**
 * In order to add the support for new API in the most recent JDK execute:
 * <ol>
 *   <li>Generate apiXXX.txt by running [com.intellij.codeInspection.tests.JavaApiUsageInspectionTestBase#testCollectSinceApiUsages]</li>
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

  override fun createOptionsPanel(): JComponent {
    val projectRb = JRadioButton(JavaBundle.message("radio.button.respecting.to.project.language.level.settings"))
    val customRb = JRadioButton(JavaBundle.message("radio.button.higher.than"))
    val panel = JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 5, true, false)).apply {
      add(JLabel(JavaBundle.message("label.forbid.api.usages")))
      add(projectRb)
      add(customRb)
      ButtonGroup().apply {
        add(projectRb)
        add(customRb)
      }
    }

    val llCombo = object : ComboBox<LanguageLevel>(LanguageLevel.values()) {
      override fun setEnabled(b: Boolean) {
        if (b == customRb.isSelected) {
          super.setEnabled(b)
        }
      }
    }.apply {
      selectedItem = if (effectiveLanguageLevel != null) effectiveLanguageLevel else LanguageLevel.JDK_1_3
      renderer = SimpleListCellRenderer.create("") { obj -> obj.presentableText }
      addActionListener { effectiveLanguageLevel = selectedItem as LanguageLevel }
    }


    val comboPanel = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.emptyLeft(20)
      add(llCombo, BorderLayout.WEST)
    }
    panel.add(comboPanel)

    val actionListener = ActionListener {
      if (projectRb.isSelected) {
        effectiveLanguageLevel = null
      }
      else {
        effectiveLanguageLevel = llCombo.selectedItem as LanguageLevel
      }
      UIUtil.setEnabled(comboPanel, !projectRb.isSelected, true)
    }
    projectRb.addActionListener(actionListener)
    customRb.addActionListener(actionListener)
    projectRb.isSelected = effectiveLanguageLevel == null
    customRb.isSelected = effectiveLanguageLevel != null
    UIUtil.setEnabled(comboPanel, !projectRb.isSelected, true)
    return panel
  }

  override fun readSettings(node: Element) {
    val element = node.getChild(EFFECTIVE_LL)
    if (element != null) {
      effectiveLanguageLevel = element.getAttributeValue("value")?.let { LanguageLevel.valueOf(it) }
    }
  }

  override fun writeSettings(node: Element) {
    if (effectiveLanguageLevel != null) {
      val llElement = Element(EFFECTIVE_LL)
      llElement.setAttribute("value", effectiveLanguageLevel.toString())
      node.addContent(llElement)
    }
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastVisitorAdapter(JavaApiUsageVisitor(JavaApiUsageProcessor(isOnTheFly, holder), holder), true)

  inner class JavaApiUsageVisitor(
    apiUsageProcessor: ApiUsageProcessor,
    private val holder: ProblemsHolder
  ) : ApiUsageUastVisitor(apiUsageProcessor) {
    override fun visitClass(node: UClass): Boolean {
      val javaPsi = node.javaPsi
      if (!javaPsi.hasModifierProperty(PsiModifier.ABSTRACT) && javaPsi !is PsiTypeParameter) { // Don't go into classes (anonymous, locals).
        val module = ModuleUtilCore.findModuleForPsiElement(javaPsi) ?: return true
        val effectiveLanguageLevel = getEffectiveLanguageLevel(module)
        if (!effectiveLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
          val version = JavaVersionService.getInstance().getJavaSdkVersion(javaPsi)
          if (version != null && version.isAtLeast(JavaSdkVersion.JDK_1_8)) {
            val mSignatures = javaPsi.visibleSignatures.filter { defaultMethods.contains(LanguageLevelUtil.getSignature(it.method)) }
            if (mSignatures.isNotEmpty()) {
              val toHighlight = node.uastAnchor?.sourcePsi ?: return true
              val jdkName = LanguageLevelUtil.getJdkName(effectiveLanguageLevel)
              val message = if (mSignatures.size == 1) {
                JvmAnalysisBundle.message("jvm.inspections.1.8.problem.single.descriptor", mSignatures.first().name, jdkName)
              } else {
                JvmAnalysisBundle.message("jvm.inspections.1.8.problem.descriptor", mSignatures.size, jdkName)
              }
              holder.registerProblem(toHighlight, message, QuickFixFactory.getInstance().createImplementMethodsFix(javaPsi))
            }
          }
        }
      }
      return true
    }
  }

  inner class JavaApiUsageProcessor(private val isOnTheFly: Boolean, private val holder: ProblemsHolder) : ApiUsageProcessor {
    override fun processConstructorInvocation(
      sourceNode: UElement, instantiatedClass: PsiClass, constructor: PsiMethod?, subclassDeclaration: UClass?
    ) {
      constructor ?: return
      val sourcePsi = sourceNode.sourcePsi ?: return
      val module = ModuleUtilCore.findModuleForPsiElement(sourcePsi) ?: return
      val languageLevel = getEffectiveLanguageLevel(module)
      val sinceLanguageLevel = LanguageLevelUtil.getLastIncompatibleLanguageLevel(constructor, languageLevel) ?: return
      registerError(sourcePsi, sinceLanguageLevel)
    }

    override fun processMethodOverriding(method: UMethod, overriddenMethod: PsiMethod) {
      val sourcePsi = method.sourcePsi ?: return
      val module = ModuleUtilCore.findModuleForPsiElement(sourcePsi) ?: return
      val languageLevel = getEffectiveLanguageLevel(module)
      val sinceLanguageLevel = LanguageLevelUtil.getLastIncompatibleLanguageLevel(overriddenMethod, languageLevel) ?: return
      val toHighlight = method.findAnnotation("java.lang.Override")?.uastAnchor?.sourcePsi ?: method.uastAnchor?.sourcePsi ?: return
      registerError(toHighlight, sinceLanguageLevel)
    }

    override fun processReference(sourceNode: UElement, target: PsiModifierListOwner, qualifier: UExpression?) {
      val sourcePsi = sourceNode.sourcePsi ?: return
      if (target !is PsiMember) return
      val module = ModuleUtilCore.findModuleForPsiElement(sourcePsi) ?: return
      val languageLevel = getEffectiveLanguageLevel(module)
      val sinceLanguageLevel = LanguageLevelUtil.getLastIncompatibleLanguageLevel(target, languageLevel)
      if (sinceLanguageLevel != null) {
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
        registerError(sourcePsi, sinceLanguageLevel)
      }
      else if (target is PsiClass && !languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
        for (generifiedClass in generifiedClasses) {
          if (InheritanceUtil.isInheritor(target, generifiedClass) && !isRawInheritance(generifiedClass, target, mutableSetOf())) {
            val message = JvmAnalysisBundle.message(
              "jvm.inspections.1.7.problem.descriptor",
              LanguageLevelUtil.getJdkName(languageLevel)
            )
            holder.registerProblem(sourcePsi, message)
            break
          }
        }
      }
    }

    private fun registerError(reference: PsiElement, sinceLanguageLevel: LanguageLevel) {
      val targetLanguageLevel = LanguageLevelUtil.getNextLanguageLevel(sinceLanguageLevel) ?: run {
        logger.error("Unable to get the next language level for $sinceLanguageLevel")
        return
      }
      val message = JvmAnalysisBundle.message(
        "jvm.inspections.1.5.problem.descriptor", LanguageLevelUtil.getShortMessage(sinceLanguageLevel)
      )
      val fix = if (isOnTheFly) {
        QuickFixFactory.getInstance().createIncreaseLanguageLevelFix(targetLanguageLevel) as LocalQuickFix
      }
      else null
      holder.registerProblem(reference, message, fix)
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

  companion object {
    private val logger = logger<JavaApiUsageInspection>()

    private var effectiveLanguageLevel: LanguageLevel? = null

    private const val EFFECTIVE_LL = "effectiveLL"

    private val ignored6ClassesApi = JavaApiUsageInspection::class.java.getResource("ignore6List.txt")?.let {
      LanguageLevelUtil.loadSignatureList(it)
    } ?: run {
      logger.warn("Could not load ignore list.")
      emptySet<String>()
    }

    private val generifiedClasses = hashSetOf("javax.swing.JComboBox", "javax.swing.ListModel", "javax.swing.JList")

    private val defaultMethods = hashSetOf("java.util.Iterator#remove()")

    fun getEffectiveLanguageLevel(module: Module): LanguageLevel {
      return effectiveLanguageLevel ?: LanguageLevelUtil.getEffectiveLanguageLevel(module)
    }
  }
}