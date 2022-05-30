// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.*
import com.intellij.codeInspection.test.junit.references.MethodSourceReference
import com.intellij.codeInspection.util.SpecialAnnotationsUtil
import com.intellij.lang.jvm.JvmMethod
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.lang.jvm.actions.*
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.tree.java.PsiNameValuePairImpl
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.psi.util.isAncestor
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.SmartList
import com.intellij.util.castSafelyTo
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.junit.JUnitCommonClassNames.*
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import java.util.*
import java.util.stream.Collectors
import javax.swing.JComponent

class JUnitMalformedMemberInspection : AbstractBaseUastLocalInspectionTool() {
  @JvmField
  val ignorableAnnotations: List<String> = ArrayList(listOf("mockit.Mocked"))

  override fun createOptionsPanel(): JComponent = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
    ignorableAnnotations, InspectionGadgetsBundle.message("ignore.parameter.if.annotated.by")
  )

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastHintedVisitorAdapter.create(
      holder.file.language,
      JUnitMalformedSignatureVisitor(holder, isOnTheFly, ignorableAnnotations),
      arrayOf(UClass::class.java, UField::class.java, UMethod::class.java),
      directOnly = true
    )
}

private class JUnitMalformedSignatureVisitor(
  private val holder: ProblemsHolder,
  private val isOnTheFly: Boolean,
  private val ignorableAnnotations: List<String>
) : AbstractUastNonRecursiveVisitor() {
  override fun visitClass(node: UClass): Boolean {
    checkMalformedNestedClass(node)
    return true
  }

  override fun visitField(node: UField): Boolean {
    checkMalformedExtension(node)
    checkDataPoint(node, "Field")
    checkRuleClassRule(node, "Field", "Field")
    return true
  }

  override fun visitMethod(node: UMethod): Boolean {
    checkMalformedParameterized(node)
    checkRepeatedTest(node)
    checkBeforeAfterTestCase(node)
    checkBeforeAfterTest(node)
    checkedMalformedSetupTeardown(node)
    checkDataPoint(node, "Method")
    checkRuleClassRule(node, "Method", "Method return")
    checkTest(node)
    return true
  }

  private fun checkMalformedNestedClass(aClass: UClass) {
    val javaClass = aClass.javaPsi
    val containingClass = javaClass.containingClass
    if (containingClass != null && aClass.isStatic && javaClass.hasAnnotation(ORG_JUNIT_JUPITER_API_NESTED)) {
      val message = JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.nested.class.inspection.description")
      val fixes = createModifierQuickfixes(aClass, modifierRequest(JvmModifier.STATIC, shouldBePresent = false)) ?: return
      holder.registerUProblem(aClass, message, *fixes)
    }
  }

  private fun checkMalformedExtension(field: UField) {
    val javaField = field.javaPsi?.castSafelyTo<PsiField>() ?: return
    val type = javaField.type
    if (javaField.hasAnnotation(ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION)) {
      if (!type.isInheritorOf(ORG_JUNIT_JUPITER_API_EXTENSION)) {
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit5.malformed.extension.registration.message",
          javaField.type.canonicalText, ORG_JUNIT_JUPITER_API_EXTENSION
        )
        holder.registerUProblem(field, message)
      }
      else if (!field.isStatic && (type.isInheritorOf(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_ALL_CALLBACK) ||
                                   type.isInheritorOf(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_ALL_CALLBACK))
      ) {
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit5.malformed.extension.class.level.message", javaField.type.presentableText
        )
        val fixes = createModifierQuickfixes(field, modifierRequest(JvmModifier.STATIC, shouldBePresent = true)) ?: return
        holder.registerUProblem(field, message, *fixes)
      }
    }
  }

  private fun checkRuleClassRule(
    declaration: UDeclaration,
    memberDescription: @NlsSafe String,
    memberTypeDescription: @NlsSafe String
  ) {
    val sourcePsi = declaration.sourcePsi ?: return
    val javaPsi = declaration.javaPsi
    if (javaPsi !is PsiModifierListOwner) return
    val ruleAnnotated = AnnotationUtil.isAnnotated(javaPsi, ORG_JUNIT_RULE, 0)
    val classRuleAnnotated = AnnotationUtil.isAnnotated(javaPsi, ORG_JUNIT_CLASS_RULE, 0)
    if (ruleAnnotated || classRuleAnnotated) {
      val isStatic = declaration.isStatic
      val isPublic = declaration.visibility == UastVisibility.PUBLIC
      val issues = getIssues(isStatic, isPublic, classRuleAnnotated)
      if (issues.isNotEmpty()) {
        val ruleFqn = if (ruleAnnotated) ORG_JUNIT_RULE else ORG_JUNIT_CLASS_RULE
        val modifierMessage = when (issues.size) {
          1 -> JvmAnalysisBundle.message(
            "jvm.inspections.junit.rule.signature.problem.single.descriptor", memberDescription, ruleFqn, issues.first()
          )
          2 -> JvmAnalysisBundle.message(
            "jvm.inspections.junit.rule.signature.problem.double.descriptor", memberDescription, ruleFqn, issues.first(), issues.last()
          )
          else -> error("Amount of issues should be smaller than 2")
        }
        val actions = SmartList<IntentionAction>()
        if (ruleAnnotated && isStatic) actions.addAll(createModifierActions(declaration, modifierRequest(JvmModifier.STATIC, false)))
        if (classRuleAnnotated && !isStatic) {
          actions.addAll(createModifierActions(declaration, modifierRequest(JvmModifier.STATIC, true)))
        }
        actions.addAll(createModifierActions(declaration, modifierRequest(JvmModifier.PUBLIC, true)))
        val quickfixes = IntentionWrapper.wrapToQuickFixes(actions, sourcePsi.containingFile).toTypedArray()
        holder.registerUProblem(declaration, modifierMessage, *quickfixes)
      }

      val actions = SmartList<IntentionAction>()
      val type = when (declaration) {
        is UMethod -> declaration.returnType
        is UField -> declaration.type
        else -> throw IllegalStateException("Expected method or field.")
      }
      val aClass = PsiUtil.resolveClassInClassTypeOnly(type)
      val isTestRuleInheritor = InheritanceUtil.isInheritor(aClass, false, ORG_JUNIT_RULES_TEST_RULE)
      if (isTestRuleInheritor) return
      val isMethodRuleInheritor = InheritanceUtil.isInheritor(aClass, false, ORG_JUNIT_RULES_METHOD_RULE)
      val typeErrorMessage = when {
        ruleAnnotated && !isMethodRuleInheritor -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.rule.type.problem.descriptor", memberTypeDescription, ORG_JUNIT_RULES_TEST_RULE,
          ORG_JUNIT_RULES_METHOD_RULE
        )
        classRuleAnnotated-> JvmAnalysisBundle.message(
          "jvm.inspections.junit.class.rule.type.problem.descriptor", memberTypeDescription, ORG_JUNIT_RULES_TEST_RULE
        )
        else -> null
      } ?: return
      val quickFix = IntentionWrapper.wrapToQuickFixes(actions, sourcePsi.containingFile).toTypedArray()
      holder.registerUProblem(declaration, typeErrorMessage, *quickFix)
    }
  }

  private fun getIssues(isStatic: Boolean, isPublic: Boolean, shouldBeStatic: Boolean): List<@NlsSafe String> = SmartList<String>().apply {
    if (!isPublic) add("'public'")
    when {
      isStatic && !shouldBeStatic -> add("non-static")
      !isStatic && shouldBeStatic -> add("'static'")
    }
  }

  private fun checkTest(method: UMethod) {
    val sourcePsi = method.sourcePsi ?: return
    val alternatives = UastFacade.convertToAlternatives(sourcePsi, arrayOf(UMethod::class.java, UMethod::class.java))
    val javaMethod = alternatives.firstOrNull { it.isStatic } ?: alternatives.firstOrNull() ?: return // gets synthetic static method in case of Kotlin
    if (method.isConstructor) return
    if (!TestUtils.isJUnit3TestMethod(javaMethod.javaPsi) && !TestUtils.isJUnit4TestMethod(javaMethod.javaPsi)) return
    val containingClass = method.javaPsi.containingClass ?: return
    if (AnnotationUtil.isAnnotated(containingClass, TestUtils.RUN_WITH, AnnotationUtil.CHECK_HIERARCHY)) return
    if (PsiType.VOID != method.returnType || method.visibility != UastVisibility.PUBLIC) {
      val message = JvmAnalysisBundle.message("jvm.inspections.test.method.is.public.void.no.arg.problem.public.void")
      holder.registerUProblem(method, message, MakeNoArgVoidFix(method.name, false, JvmModifier.PUBLIC))
    }
    val parameterList = method.uastParameters
    if (parameterList.isNotEmpty() && parameterList.any { param ->
        param.javaPsi?.castSafelyTo<PsiParameter>()?.let { !AnnotationUtil.isAnnotated(it, ignorableAnnotations, 0) } == true
      }) {
      val message = JvmAnalysisBundle.message("jvm.inspections.test.method.is.public.void.no.arg.problem.no.param")
      holder.registerUProblem(method, message, MakeNoArgVoidFix(method.name, false, JvmModifier.PUBLIC))
    }
    if (javaMethod.isStatic) {
      val message = JvmAnalysisBundle.message("jvm.inspections.test.method.is.public.void.no.arg.problem.static")
      holder.registerUProblem(method, message, MakeNoArgVoidFix(method.name, false, JvmModifier.PUBLIC))
    }
  }

  private fun checkDataPoint(declaration: UDeclaration, memberDescription: @NlsSafe String) {
    val javaDecl = declaration.javaPsi.castSafelyTo<PsiMember>() ?: return
    val annotation = DATAPOINT_S.firstOrNull { AnnotationUtil.isAnnotated(javaDecl, it, 0) } ?: return
    val issues = getIssues(declaration)
    if (issues.isNotEmpty()) {
      val message = if (issues.size == 1) JvmAnalysisBundle.message(
        "jvm.inspections.junit.datapoint.problem.single.descriptor", memberDescription, annotation, issues.first()
      )
      else JvmAnalysisBundle.message( // size should always be 2
        "jvm.inspections.junit.datapoint.problem.double.descriptor", memberDescription, annotation, issues.first(), issues.last()
      )
      val name = declaration.uastAnchor?.sourcePsi?.text ?: return
      holder.registerUProblem(declaration, message, MakePublicStaticQuickfix(memberDescription, name, issues))
    }
  }

  private fun getIssues(declaration: UDeclaration): List<@NlsSafe String> = SmartList<String>().apply {
    if (declaration.visibility != UastVisibility.PUBLIC) add("public")
    if (!declaration.isStatic) add("static")
  }

  private fun checkRepeatedTest(method: UMethod) {
    val srcMethod = method.sourcePsi ?: return
    val javaMethod = method.javaPsi
    val repeatedAnno = method.findAnnotation(ORG_JUNIT_JUPITER_API_REPEATED_TEST)
    if (repeatedAnno != null) {
      val testAnno = method.findAnnotations(
        ORG_JUNIT_JUPITER_API_TEST, ORG_JUNIT_JUPITER_API_TEST_FACTORY
      )
      val toHighlight = testAnno.firstOrNull()?.sourcePsi
      if (testAnno.isNotEmpty() && toHighlight != null) {
        holder.registerProblem(toHighlight,
                               JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.repetition.description.suspicious.combination"),
                               DeleteElementFix(toHighlight)
        )
      }
      val repeatedNumber = repeatedAnno.findDeclaredAttributeValue("value")
      if (repeatedNumber != null) {
        val constant = repeatedNumber.evaluate()
        val repeatedSrcPsi = repeatedNumber.sourcePsi
        if (repeatedSrcPsi != null && constant is Int && constant <= 0) {
          holder.registerProblem(repeatedSrcPsi,
                                 JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.repetition.description.positive.number")
          )
        }
      }
    }
    else {
      val psiFacade = JavaPsiFacade.getInstance(holder.project)
      val repetitionInfo = psiFacade.findClass(
        ORG_JUNIT_JUPITER_API_REPETITION_INFO, srcMethod.resolveScope
      ) ?: return
      val repetitionType = JavaPsiFacade.getElementFactory(holder.project).createType(repetitionInfo)
      val repetitionInfoParam = method.uastParameters.find { it.type == repetitionType }
      val paramAnchor = repetitionInfoParam?.uastAnchor?.sourcePsi
      if (repetitionInfoParam != null) {
        if (MetaAnnotationUtil.isMetaAnnotated(javaMethod, NON_REPEATED_ANNOTATIONS)) {
          holder.registerProblem(paramAnchor ?: repetitionInfoParam,
                                 JvmAnalysisBundle.message(
                                   "jvm.inspections.junit5.malformed.repetition.description.injected.for.repeatedtest")
          )
        }
        else {
          val anno = MetaAnnotationUtil.findMetaAnnotations(javaMethod, BEFORE_AFTER_ALL).findFirst().orElse(null)
          if (anno != null) {
            val qName = anno.qualifiedName
            holder.registerProblem(paramAnchor ?: repetitionInfoParam,
                                   JvmAnalysisBundle.message(
                                     "jvm.inspections.junit5.malformed.repetition.description.injected.for.each",
                                     StringUtil.getShortName(qName!!))
            )
          }
          else {
            if (MetaAnnotationUtil.isMetaAnnotated(javaMethod, BEFORE_AFTER_EACH)
                && javaMethod.containingClass?.methods?.find {
                MetaAnnotationUtil.isMetaAnnotated(it, NON_REPEATED_ANNOTATIONS)
              } != null
            ) {
              holder.registerProblem(paramAnchor ?: repetitionInfoParam,
                                     JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.repetition.description.injected.for.test")
              )
            }
          }
        }
      }
    }
  }

  private fun checkedMalformedSetupTeardown(method: UMethod) {
    if ("setUp" != method.name && "tearDown" != method.name) return
    val targetClass = method.javaPsi.containingClass
    if (!InheritanceUtil.isInheritor(targetClass, JUNIT_FRAMEWORK_TEST_CASE)) return
    if (method.uastParameters.isNotEmpty() || PsiType.VOID != method.returnType ||
        method.visibility != UastVisibility.PUBLIC && method.visibility != UastVisibility.PROTECTED
    ) {
      val message = JvmAnalysisBundle.message("jvm.inspections.malformed.set.up.tear.down.problem.descriptor")
      holder.registerUProblem(method, message, MakeNoArgVoidFix(method.name, false, JvmModifier.PUBLIC))
    }
  }
  
  private fun isJUnit4(ann: String) = ann.endsWith(ORG_JUNIT_BEFORE) || ann.endsWith(ORG_JUNIT_AFTER)

  private fun isJUnit5(ann: String) = ann.endsWith(ORG_JUNIT_JUPITER_API_BEFORE_EACH) || ann.endsWith(ORG_JUNIT_JUPITER_API_AFTER_EACH)

  private fun List<UParameter>.isValidBeforeTest(): Boolean {
    return isEmpty() || all {
      it.type.canonicalText == ORG_JUNIT_JUPITER_API_TEST_INFO || it.type.canonicalText == ORG_JUNIT_JUPITER_API_REPETITION_INFO
        || it.type.canonicalText == ORG_JUNIT_JUPITER_API_TEST_REPORTER
    }
  }

  private fun checkBeforeAfterTest(method: UMethod) {
    val javaMethod = method.javaPsi
    val annotation = BEFORE_AFTER_TEST.firstOrNull { AnnotationUtil.isAnnotated(javaMethod, it, AnnotationUtil.CHECK_HIERARCHY) } ?: return
    val returnType = method.returnType ?: return
    val parameterList = method.uastParameters
    if (isJUnit4(annotation) &&
        (!javaMethod.hasModifier(JvmModifier.PUBLIC) || returnType != PsiType.VOID || javaMethod.hasModifier(JvmModifier.STATIC) || parameterList.isNotEmpty())
    ) {
      holder.registerUProblem(
        method,
        JvmAnalysisBundle.message("jvm.inspections.before.after.descriptor", annotation),
        MakeNoArgVoidFix(method.name, false, JvmModifier.PUBLIC)
      )
    }

    if (isJUnit5(annotation) &&
        (javaMethod.hasModifier(JvmModifier.PRIVATE) || returnType != PsiType.VOID || javaMethod.hasModifier(JvmModifier.STATIC) || !parameterList.isValidBeforeTest())
    ) {
      holder.registerUProblem(
        method,
        JvmAnalysisBundle.message("jvm.inspections.before.after.descriptor", annotation),
        MakeNoArgVoidFix(method.name, false, JvmModifier.PUBLIC)
      )
    }
  }

  private fun UMethod.isValidParameterList(alternatives: List<UMethod>): Boolean {
    if (uastParameters.isEmpty()) return true
    if (uastParameters.size != 1) return false
    val extension = alternatives.firstNotNullOfOrNull {
      it.javaPsi.containingClass?.getAnnotation(ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH)?.findAttributeValue("value")
    }?.toUElement() ?: return false
    if (extension !is UClassLiteralExpression) return false
    return InheritanceUtil.isInheritor(extension.type, ORG_JUNIT_JUPITER_API_EXTENSION_PARAMETER_RESOLVER)
  }

  private fun checkBeforeAfterTestCase(method: UMethod) {
    val sourcePsi = method.sourcePsi ?: return
    val javaMethod = method.javaPsi
    val annotation = BEFORE_AFTER_TEST_CASE.firstOrNull {
      AnnotationUtil.isAnnotated(javaMethod, it, AnnotationUtil.CHECK_HIERARCHY)
    } ?: return
    val returnsVoid = method.returnType == PsiType.VOID
    // We get alternatives because Kotlin generates 2 methods for each `JvmStatic` annotated method
    val alternatives = UastFacade.convertToAlternatives(sourcePsi, arrayOf(UMethod::class.java)).toList()
    if (annotation.endsWith("Class")) { // JUnit 4 annotation
      val isStatic = alternatives.any { it.isStatic }
      val isPublic = javaMethod.hasModifier(JvmModifier.PUBLIC)
      if (!isStatic || !returnsVoid || !method.isValidParameterList(alternatives) || !isPublic) {
        holder.registerUProblem(
          method,
          JvmAnalysisBundle.message("jvm.inspections.before.after.descriptor", annotation),
          MakeNoArgVoidFix(method.name, true, JvmModifier.PUBLIC)
        )
      }
    }
    else { // JUnit 5 annotation
      val isPrivate = javaMethod.hasModifier(JvmModifier.PRIVATE)
      val inTestInstance = alternatives.any { it.isStatic }
                           || javaMethod.containingClass?.let { cls -> TestUtils.testInstancePerClass(cls) } ?: false
      if (!inTestInstance || !returnsVoid || !method.isValidParameterList(alternatives) || isPrivate) {
        holder.registerUProblem(
          method,
          JvmAnalysisBundle.message("jvm.inspections.before.after.descriptor", annotation),
          MakeNoArgVoidFix(method.name, true, JvmModifier.PUBLIC)
        )
      }
    }
  }

  private fun checkMalformedParameterized(method: UMethod) {
    val parameterizedAnnotation = method.findAnnotations(ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST).firstOrNull()
    val testAnnotations = method.findAnnotations(
      ORG_JUNIT_JUPITER_API_TEST,
      ORG_JUNIT_JUPITER_API_TEST_FACTORY
    )
    if (parameterizedAnnotation != null) {
      val firstAnnotation = testAnnotations.firstOrNull()
      if (method.uastParameters.isNotEmpty() && firstAnnotation != null) {
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit5.malformed.parameterized.inspection.description.suspicious.combination.test.and.parameterizedtest"
        )
        val fix = DeleteElementFix(testAnnotations.first().sourcePsi ?: return)
        holder.registerUProblem(firstAnnotation, message, fix)
      }
      val usedSourceAnnotations = MetaAnnotationUtil.findMetaAnnotations(method.javaPsi, SOURCE_ANNOTATIONS)
        .collect(Collectors.toList()).toList()

      checkConflictingSourceAnnotations(usedSourceAnnotations.associateWith { it.qualifiedName }, method, parameterizedAnnotation)
      usedSourceAnnotations.forEach {
        when (it.qualifiedName) {
          ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE -> checkMethodSource(method, it)
          ORG_JUNIT_JUPITER_PARAMS_VALUES_SOURCE -> checkValuesSource(method, it)
          ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE -> checkEnumSource(method, it)
          ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE -> checkCsvSource(it)
          ORG_JUNIT_JUPITER_PARAMS_NULL_SOURCE -> checkNullSource(method, it)
          ORG_JUNIT_JUPITER_PARAMS_EMPTY_SOURCE -> checkEmptySource(method, it)
          ORG_JUNIT_JUPITER_PARAMS_NULL_AND_EMPTY_SOURCE -> {
            checkNullSource(method, it)
            checkEmptySource(method, it)
          }
        }
      }
    }
    else {
      if (testAnnotations.isNotEmpty()
          && MetaAnnotationUtil.isMetaAnnotated(method.javaPsi, SOURCE_ANNOTATIONS)
          && testAnnotations.first().sourcePsi != null
          && testAnnotations.first().javaPsi != null
      ) {
        val place = testAnnotations.first().uastAnchor?.sourcePsi ?: return
        val fix = ChangeAnnotationFix(
          testAnnotations.first().javaPsi!!,
          ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST
        )
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit5.malformed.parameterized.inspection.description.suspicious.combination")
        holder.registerProblem(place, message, fix)
      }
    }
  }

  private fun checkConflictingSourceAnnotations(annMap: Map<PsiAnnotation, @NlsSafe String?>, method: UMethod, place: UAnnotation) {
    val singleParameterProviders = annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE) ||
                                   annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_VALUES_SOURCE) ||
                                   annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_NULL_SOURCE) ||
                                   annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_EMPTY_SOURCE) ||
                                   annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_NULL_AND_EMPTY_SOURCE)

    val multipleParametersProvider = annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE)
                                     || annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE)
                                     || annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_SOURCE)

    if (!multipleParametersProvider && !singleParameterProviders && hasCustomProvider(annMap)) return
    if (!multipleParametersProvider) {
      val message = if (!singleParameterProviders) {
        JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.parameterized.inspection.description.no.sources.are.provided")
      }
      else if (hasMultipleParameters(method.javaPsi)) {
        JvmAnalysisBundle.message(
          "jvm.inspections.junit5.malformed.parameterized.inspection.description.multiple.parameters.are.not.supported.by.this.source")
      }
      else return
      holder.registerUProblem(place, message)
    }
  }

  private fun hasCustomProvider(annotations: Map<PsiAnnotation, String?>): Boolean {
    annotations.forEach { (anno, qName) ->
      when (qName) {
        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCE -> return@hasCustomProvider true
        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCES -> {
          val attributes = anno.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)
          if ((attributes as? PsiArrayInitializerMemberValue)?.initializers?.isNotEmpty() == true) return@hasCustomProvider true
        }
      }
    }
    return false
  }

  private fun checkMethodSource(method: UMethod, methodSource: PsiAnnotation) {
    val psiMethod = method.javaPsi
    val containingClass = psiMethod.containingClass ?: return
    val annotationMemberValue = methodSource.findDeclaredAttributeValue("value")
    if (annotationMemberValue == null) {
      if (methodSource.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME) == null) return
      val foundMethod = containingClass.findMethodsByName(method.name, true).singleOrNull { it.parameters.isEmpty() }
      val uFoundMethod = foundMethod.toUElementOfType<UMethod>()
      if (uFoundMethod != null) {
        return checkSourceProvider(uFoundMethod, containingClass, methodSource, method)
      }
      else {
        return highlightAbsentSourceProvider(containingClass, methodSource, method.name, method)
      }
    }
    else {
      annotationMemberValue.nestedValues().forEach { attributeValue ->
        for (reference in attributeValue.references) {
          if (reference is MethodSourceReference) {
            val resolve = reference.resolve()
            if (resolve !is PsiMethod) {
              return highlightAbsentSourceProvider(containingClass, attributeValue, reference.value, method)
            }
            else {
              val sourceProvider: PsiMethod = resolve
              val uSourceProvider = sourceProvider.toUElementOfType<UMethod>() ?: return
              return checkSourceProvider(uSourceProvider, containingClass, attributeValue, method)
            }
          }
        }
      }
    }
  }

  private fun highlightAbsentSourceProvider(
    containingClass: PsiClass, attributeValue: PsiElement, sourceProviderName: String, method: UMethod
  ) {
    if (isOnTheFly) {
      val modifiers = mutableListOf(JvmModifier.PUBLIC)
      if (!TestUtils.testInstancePerClass(containingClass)) modifiers.add(JvmModifier.STATIC)
      val typeFromText = JavaPsiFacade.getElementFactory(containingClass.project).createTypeFromText(
        METHOD_SOURCE_RETURN_TYPE, containingClass
      )
      val request = methodRequest(containingClass.project, sourceProviderName, modifiers, typeFromText)
      val actions = createMethodActions(containingClass, request)
      val quickFixes = IntentionWrapper.wrapToQuickFixes(actions, containingClass.containingFile).toTypedArray()
      val message = JvmAnalysisBundle.message(
        "jvm.inspections.junit5.malformed.parameterized.inspection.description.method.source.unresolved", sourceProviderName)
      val place = (if (method.javaPsi.isAncestor(attributeValue, true)) attributeValue
      else method.javaPsi.nameIdentifier ?: method.javaPsi).toUElement()?.sourcePsi ?: return
      return holder.registerProblem(place, message, *quickFixes)
    }
  }

  private fun checkSourceProvider(sourceProvider: UMethod, containingClass: PsiClass?, attributeValue: PsiElement, method: UMethod) {
    val place = (if (method.javaPsi.isAncestor(attributeValue, true)) attributeValue
    else method.javaPsi.nameIdentifier ?: method.javaPsi).toUElement()?.sourcePsi ?: return
    val providerName = sourceProvider.name
    if (!sourceProvider.isStatic &&
        containingClass != null && !TestUtils.testInstancePerClass(containingClass) &&
        !implementationsTestInstanceAnnotated(containingClass)
    ) {
      val annotation = JavaPsiFacade.getElementFactory(containingClass.project).createAnnotationFromText(
        TEST_INSTANCE_PER_CLASS, containingClass
      )
      val actions = mutableListOf<IntentionAction>()
      val value = (annotation.attributes.first() as PsiNameValuePairImpl).value
      if (value != null) {
        actions.addAll(createAddAnnotationActions(
          containingClass,
          annotationRequest(ORG_JUNIT_JUPITER_API_TEST_INSTANCE, constantAttribute("value", value.text))
        ))
      }
      actions.addAll(createModifierActions(sourceProvider, modifierRequest(JvmModifier.STATIC, true)))
      val quickFixes = IntentionWrapper.wrapToQuickFixes(actions, sourceProvider.javaPsi.containingFile).toTypedArray()
      val message = JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.parameterized.inspection.description.method.source.static",
                                              providerName)
      holder.registerProblem(place, message, *quickFixes)
    }
    else if (sourceProvider.uastParameters.isNotEmpty()) {
      val message = JvmAnalysisBundle.message(
        "jvm.inspections.junit5.malformed.parameterized.inspection.description.method.source.no.params", providerName)
      holder.registerProblem(place, message)
    }
    else {
      val componentType = getComponentType(sourceProvider.returnType, method.javaPsi)
      if (componentType == null) {
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit5.malformed.parameterized.inspection.description.method.source.return.type", providerName)
        holder.registerProblem(place, message)
      }
      else if (hasMultipleParameters(method.javaPsi)
               && !InheritanceUtil.isInheritor(componentType, ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS)
               && !componentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
               && !componentType.deepComponentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
      ) {
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit5.malformed.parameterized.inspection.description.wrapped.in.arguments")
        holder.registerProblem(place, message)
      }
    }
  }

  private fun implementationsTestInstanceAnnotated(containingClass: PsiClass): Boolean =
    ClassInheritorsSearch.search(containingClass, containingClass.resolveScope, true).any { TestUtils.testInstancePerClass(it) }

  private fun getComponentType(returnType: PsiType?, method: PsiMethod): PsiType? {
    val collectionItemType = JavaGenericsUtil.getCollectionItemType(returnType, method.resolveScope)
    if (collectionItemType != null) return collectionItemType
    if (InheritanceUtil.isInheritor(returnType, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM)) return PsiType.INT
    if (InheritanceUtil.isInheritor(returnType, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM)) return PsiType.LONG
    if (InheritanceUtil.isInheritor(returnType, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM)) return PsiType.DOUBLE
    val streamItemType = PsiUtil.substituteTypeParameter(returnType, CommonClassNames.JAVA_UTIL_STREAM_STREAM, 0, true)
    if (streamItemType != null) return streamItemType
    return PsiUtil.substituteTypeParameter(returnType, CommonClassNames.JAVA_UTIL_ITERATOR, 0, true)
  }


  private fun hasMultipleParameters(method: PsiMethod): Boolean {
    val containingClass = method.containingClass
    return containingClass != null
           && method.parameterList.parameters.count {
      !InheritanceUtil.isInheritor(it.type, ORG_JUNIT_JUPITER_API_TEST_INFO) &&
      !InheritanceUtil.isInheritor(it.type, ORG_JUNIT_JUPITER_API_TEST_REPORTER)
    } > 1 && !MetaAnnotationUtil.isMetaAnnotatedInHierarchy(
      containingClass, listOf(ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH)
    )
  }

  private fun checkNullSource(
    method: UMethod, psiAnnotation: PsiAnnotation
  ) {
    val size = method.uastParameters.size
    if (size != 1) {
      val sourcePsi = (if (method.javaPsi.isAncestor(psiAnnotation, true)) psiAnnotation
      else method.javaPsi.nameIdentifier ?: method.javaPsi).toUElement()?.sourcePsi ?: return
      return checkFormalParameters(size, sourcePsi, psiAnnotation.qualifiedName)
    }
  }

  private fun checkEmptySource(
    method: UMethod, psiAnnotation: PsiAnnotation
  ) {
    val sourcePsi = (if (method.javaPsi.isAncestor(psiAnnotation, true)) psiAnnotation
    else method.javaPsi.nameIdentifier ?: method.javaPsi).toUElement()?.sourcePsi ?: return
    val size = method.uastParameters.size
    val shortName = psiAnnotation.qualifiedName ?: return
    if (size == 1) {
      var type = method.uastParameters.first().type
      if (type is PsiClassType) type = type.rawType()
      if (type is PsiArrayType
          || type.equalsToText(CommonClassNames.JAVA_LANG_STRING)
          || type.equalsToText(CommonClassNames.JAVA_UTIL_LIST)
          || type.equalsToText(CommonClassNames.JAVA_UTIL_SET)
          || type.equalsToText(CommonClassNames.JAVA_UTIL_MAP)
      ) return
      val message = JvmAnalysisBundle.message(
        "jvm.inspections.junit5.malformed.parameterized.inspection.description.emptysource.cannot.provide.argument",
        StringUtil.getShortName(shortName), type.presentableText)
      holder.registerProblem(sourcePsi, message)
    }
    else {
      checkFormalParameters(size, sourcePsi, shortName)
    }
  }

  private fun checkFormalParameters(
    size: Int, sourcePsi: PsiElement, sourceName: String?
  ) {
    if (sourceName == null) return
    val message = if (size == 0) {
      JvmAnalysisBundle.message(
        "jvm.inspections.junit5.malformed.parameterized.inspection.description.nullsource.cannot.provide.argument.no.params",
        StringUtil.getShortName(sourceName))
    }
    else {
      JvmAnalysisBundle.message(
        "jvm.inspections.junit5.malformed.parameterized.inspection.description.nullsource.cannot.provide.argument.too.many.params",
        StringUtil.getShortName(sourceName))
    }
    holder.registerProblem(sourcePsi, message)
  }

  private fun checkEnumSource(method: UMethod, enumSource: PsiAnnotation) {
    // @EnumSource#value type is Class<?>, not an array
    val value = enumSource.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)
    if (value is PsiClassObjectAccessExpression) {
      val enumType = value.operand.type
      checkSourceTypeAndParameterTypeAgree(method, value, enumType)
      checkEnumConstants(enumSource, enumType, method)
    }
    return
  }

  private fun checkSourceTypeAndParameterTypeAgree(method: UMethod, attributeValue: PsiAnnotationMemberValue, componentType: PsiType) {
    val parameters = method.uastParameters
    if (parameters.size == 1) {
      val paramType = parameters.first().type
      if (!paramType.isAssignableFrom(componentType) && !InheritanceUtil.isInheritor(
          componentType, ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS)
      ) {
        if (componentType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          //implicit conversion to primitive/wrapper
          if (TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(paramType)) return
          val psiClass = PsiUtil.resolveClassInClassTypeOnly(paramType)
          //implicit conversion to enum
          if (psiClass != null) {
            if (psiClass.isEnum && psiClass.findFieldByName((attributeValue as PsiLiteral).value as String?, false) != null) return
            //implicit java time conversion
            val qualifiedName = psiClass.qualifiedName
            if (qualifiedName != null) {
              if (qualifiedName.startsWith("java.time.")) return
              if (qualifiedName == "java.nio.file.Path") return
            }

            val factoryMethod: (PsiMethod) -> Boolean = {
              !it.hasModifier(JvmModifier.PRIVATE) &&
              it.parameterList.parametersCount == 1 &&
              it.parameterList.parameters.first().type.equalsToText(CommonClassNames.JAVA_LANG_STRING)
            }

            if (!psiClass.hasModifier(JvmModifier.ABSTRACT) && psiClass.constructors.find(factoryMethod) != null) return
            if (psiClass.methods.find { it.hasModifier(JvmModifier.STATIC) && factoryMethod(it) } != null) return
          }
        }
        else if (componentType.equalsToText(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_NULL_ENUM)) {
          val psiClass = PsiUtil.resolveClassInClassTypeOnly(paramType)
          if (psiClass != null && psiClass.isEnum) return
        }
        val param = parameters.first()
        val default = param.sourcePsi as PsiNameIdentifierOwner
        val place = (if (method.javaPsi.isAncestor(attributeValue, true)) attributeValue
        else default.nameIdentifier ?: default).toUElement()?.sourcePsi ?: return
        if (param.findAnnotation(ORG_JUNIT_JUPITER_PARAMS_CONVERTER_CONVERT_WITH) != null) return
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit5.malformed.parameterized.inspection.description.method.source.assignable",
          componentType.presentableText, paramType.presentableText
        )
        holder.registerProblem(place, message)
      }
    }
  }

  private fun checkValuesSource(method: UMethod, valuesSource: PsiAnnotation) {
    val psiMethod = method.javaPsi
    val possibleValues = mapOf(
      "strings" to PsiType.getJavaLangString(psiMethod.manager, psiMethod.resolveScope),
      "ints" to PsiType.INT,
      "longs" to PsiType.LONG,
      "doubles" to PsiType.DOUBLE,
      "shorts" to PsiType.SHORT,
      "bytes" to PsiType.BYTE,
      "floats" to PsiType.FLOAT,
      "chars" to PsiType.CHAR,
      "booleans" to PsiType.BOOLEAN,
      "classes" to PsiType.getJavaLangClass(psiMethod.manager, psiMethod.resolveScope)
    )

    possibleValues.keys.forEach { valueKey ->
      valuesSource.nestedAttributeValues(valueKey)?.forEach { value ->
        possibleValues[valueKey]?.let { checkSourceTypeAndParameterTypeAgree(method, value, it) }
      }
    }

    val attributesNumber = valuesSource.parameterList.attributes.size
    val annotation = (if (method.javaPsi.isAncestor(valuesSource, true)) valuesSource
    else method.javaPsi.nameIdentifier ?: method.javaPsi).toUElementOfType<UAnnotation>() ?: return
    val message = if (attributesNumber == 0) {
      JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.parameterized.inspection.description.no.value.source.is.defined")
    }
    else if (attributesNumber > 1) {
      JvmAnalysisBundle.message(
        "jvm.inspections.junit5.malformed.parameterized.inspection.description.exactly.one.type.of.input.must.be.provided")
    }
    else return
    return holder.registerUProblem(annotation, message)
  }

  private fun checkEnumConstants(
    enumSource: PsiAnnotation, enumType: PsiType, method: UMethod
  ) {
    val mode = enumSource.findAttributeValue("mode")
    val uMode = mode.toUElement()
    if (uMode is UReferenceExpression && ("INCLUDE" == uMode.resolvedName || "EXCLUDE" == uMode.resolvedName)) {
      var validType = enumType
      if (enumType.canonicalText == ORG_JUNIT_JUPITER_PARAMS_PROVIDER_NULL_ENUM) {
        val parameters = method.uastParameters
        if (parameters.isNotEmpty()) validType = parameters.first().type
      }
      val allEnumConstants = (PsiUtil.resolveClassInClassTypeOnly(validType) ?: return).fields
        .filterIsInstance<PsiEnumConstant>()
        .map { it.name }
        .toSet()
      val definedConstants = mutableSetOf<String>()
      enumSource.nestedAttributeValues("names")?.forEach { name ->
        if (name is PsiLiteralExpression) {
          val value = name.value
          if (value is String) {
            val sourcePsi = (if (method.javaPsi.isAncestor(name, true)) name
            else method.javaPsi.nameIdentifier ?: method.javaPsi).toUElement()?.sourcePsi ?: return@forEach
            val message = if (!allEnumConstants.contains(value)) {
              JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.parameterized.inspection.description.unresolved.enum")
            }
            else if (!definedConstants.add(value)) {
              JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.parameterized.inspection.description.duplicated.enum")
            }
            else return@forEach
            holder.registerProblem(sourcePsi, message)
          }
        }
      }
    }
  }

  private fun checkCsvSource(methodSource: PsiAnnotation) {
    methodSource.nestedAttributeValues("resources")?.forEach { attributeValue ->
      for (ref in attributeValue.references) {
        if (ref.isSoft) continue
        if (ref is FileReference && ref.multiResolve(false).isEmpty()) {
          val message = JvmAnalysisBundle.message(
            "jvm.inspections.junit5.malformed.parameterized.inspection.description.file.source", attributeValue.text
          )
          holder.registerProblem(ref.element, message, *ref.quickFixes)
        }
      }
    }
  }

  private fun PsiAnnotation.nestedAttributeValues(value: String) = findAttributeValue(value)?.nestedValues()

  private fun PsiAnnotationMemberValue.nestedValues(): List<PsiAnnotationMemberValue> {
    return if (this is PsiArrayInitializerMemberValue) initializers.flatMap { it.nestedValues() } else listOf(this)
  }

  private class ChangeAnnotationFix(
    testAnnotation: PsiAnnotation, private val targetAnnotation: String
  ) : LocalQuickFixAndIntentionActionOnPsiElement(testAnnotation) {
    override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.inspections.junit5.malformed.parameterized.fix.family.name")

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
      val annotation = JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@$targetAnnotation", startElement)
      annotation.toUElementOfType<UAnnotation>()?.let { anno ->
        startElement.toUElementOfType<UAnnotation>()?.replace(anno)
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(startElement.replace(annotation))
      }
    }

    override fun getText(): String = JvmAnalysisBundle.message(
      "jvm.inspections.junit5.malformed.parameterized.fix.text", StringUtil.getShortName(targetAnnotation)
    )
  }

  private class MakePublicStaticQuickfix(
    private val memberDescription: @NlsSafe String,
    private val memberName: @NlsSafe String,
    @FileModifier.SafeFieldForPreview private val issues: List<@NlsSafe String>
  ) : LocalQuickFix {
    override fun getName(): String = if (issues.size == 1) {
      JvmAnalysisBundle.message("jvm.inspections.junit.datapoint.fix.single.name",
                                memberDescription.lowercase(Locale.getDefault()), memberName, issues.first()
      )
    } else { // size should always be 2
      JvmAnalysisBundle.message("jvm.inspections.junit.datapoint.fix.double.name",
                                memberDescription.lowercase(Locale.getDefault()), memberName, issues.first(), issues.last()
      )
    }

    override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.inspections.junit.datapoint.fix.familyName")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val containingFile = descriptor.psiElement.containingFile ?: return
      val declaration = getUParentForIdentifier(descriptor.psiElement)?.castSafelyTo<UDeclaration>()?.javaPsi ?: return
      val declarationPtr = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(declaration)
      declarationPtr.invokeModifierRequest(JvmModifier.PUBLIC, containingFile)
      declarationPtr.invokeModifierRequest(JvmModifier.STATIC, containingFile)
    }

    private fun SmartPsiElementPointer<PsiElement>.invokeModifierRequest(modifier: JvmModifier, containingFile: PsiFile) {
      element?.castSafelyTo<JvmModifiersOwner>()?.let { elem ->
        createModifierActions(elem, modifierRequest(modifier, true)).forEach {
          it.invoke(project, null, containingFile)
        }
      } ?: return
    }
  }

  private class MakeNoArgVoidFix(
    private val methodName: @NlsSafe String,
    private val makeStatic: Boolean,
    private val newVisibility: JvmModifier? = null
  ) : LocalQuickFix {
    override fun getName(): String = JvmAnalysisBundle.message("jvm.fix.make.no.arg.void.descriptor", methodName)

    override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.fix.make.no.arg.void.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val containingFile = descriptor.psiElement.containingFile ?: return
      val javaMethod = getUParentForIdentifier(descriptor.psiElement)?.castSafelyTo<UMethod>()?.javaPsi ?: return
      val methodPtr = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(javaMethod)
      methodPtr.element?.castSafelyTo<JvmMethod>()?.let { jvmMethod ->
        createChangeTypeActions(jvmMethod, typeRequest(JvmPrimitiveTypeKind.VOID.name, emptyList())).forEach {
          it.invoke(project, null, containingFile)
        }
      }

      methodPtr.element?.castSafelyTo<JvmMethod>()?.let { jvmMethod ->
        createChangeParametersActions(jvmMethod, setMethodParametersRequest(emptyMap<String, JvmType>().entries)).forEach {
          it.invoke(project, null, containingFile)
        }
      }

      if (newVisibility != null) {
        methodPtr.element?.castSafelyTo<JvmMethod>()?.let { jvmMethod ->
          createModifierActions(jvmMethod, modifierRequest(newVisibility, true)).forEach {
            it.invoke(project, null, containingFile)
          }
        }
      }

      methodPtr.element?.castSafelyTo<JvmMethod>()?.let { jvmMethod ->
        createModifierActions(jvmMethod, modifierRequest(JvmModifier.STATIC, makeStatic)).forEach {
          it.invoke(project, null, containingFile)
        }
      }
    }
  }

  companion object {
    private const val TEST_INSTANCE_PER_CLASS = "@org.junit.jupiter.api.TestInstance(TestInstance.Lifecycle.PER_CLASS)"
    private const val METHOD_SOURCE_RETURN_TYPE = "java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>"

    private val NON_REPEATED_ANNOTATIONS: List<String> = listOf(
      ORG_JUNIT_JUPITER_API_TEST,
      ORG_JUNIT_JUPITER_API_TEST_FACTORY,
      ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST
    )

    private val BEFORE_AFTER = listOf(ORG_JUNIT_BEFORE, ORG_JUNIT_AFTER)
    private val BEFORE_AFTER_CLASS = listOf(ORG_JUNIT_BEFORE_CLASS, ORG_JUNIT_AFTER_CLASS)
    private val BEFORE_AFTER_EACH = listOf(ORG_JUNIT_JUPITER_API_BEFORE_EACH, ORG_JUNIT_JUPITER_API_AFTER_EACH)
    private val BEFORE_AFTER_ALL = listOf(ORG_JUNIT_JUPITER_API_BEFORE_ALL, ORG_JUNIT_JUPITER_API_AFTER_ALL)
    private val BEFORE_AFTER_TEST = BEFORE_AFTER + BEFORE_AFTER_EACH
    private val BEFORE_AFTER_TEST_CASE = BEFORE_AFTER_CLASS + BEFORE_AFTER_ALL

    private val DATAPOINT_S = arrayOf(ORG_JUNIT_EXPERIMENTAL_THEORIES_DATAPOINT, ORG_JUNIT_EXPERIMENTAL_THEORIES_DATAPOINTS)
  }
}