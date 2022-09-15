// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil
import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.*
import com.intellij.codeInspection.test.junit.references.MethodSourceReference
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.SpecialAnnotationsUtil
import com.intellij.lang.Language
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.lang.jvm.actions.*
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.tree.java.PsiNameValuePairImpl
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.psi.util.isAncestor
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.junit.JUnitCommonClassNames.*
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import javax.swing.JComponent
import kotlin.streams.asSequence

class JUnitMalformedDeclarationInspection : AbstractBaseUastLocalInspectionTool() {
  @JvmField
  val ignorableAnnotations = mutableListOf("mockit.Mocked", "org.junit.jupiter.api.io.TempDir")

  override fun createOptionsPanel(): JComponent = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
    ignorableAnnotations, JvmAnalysisBundle.message("jvm.inspections.junit.malformed.option.ignore.test.parameter.if.annotated.by")
  )

  private fun shouldInspect(file: PsiFile) = isJUnit3InScope(file) || isJUnit4InScope(file) || isJUnit5InScope(file)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      JUnitMalformedSignatureVisitor(holder, isOnTheFly, ignorableAnnotations),
      arrayOf(UClass::class.java, UField::class.java, UMethod::class.java),
      directOnly = true
    )
  }
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
    dataPoint.report(holder, node)
    ruleSignatureProblem.report(holder, node)
    classRuleSignatureProblem.report(holder, node)
    return true
  }

  override fun visitMethod(node: UMethod): Boolean {
    checkMalformedParameterized(node)
    checkRepeatedTestNonPositive(node)
    checkIllegalCombinedAnnotations(node)
    dataPoint.report(holder, node)
    checkSuite(node)
    checkedMalformedSetupTeardown(node)
    beforeAfterProblem.report(holder, node)
    beforeAfterEachProblem.report(holder, node)
    beforeAfterClassProblem.report(holder, node)
    beforeAfterAllProblem.report(holder, node)
    ruleSignatureProblem.report(holder, node)
    classRuleSignatureProblem.report(holder, node)
    checkJUnit3Test(node)
    junit4TestProblem.report(holder, node)
    junit5TestProblem.report(holder, node)
    return true
  }

  private fun checkMalformedNestedClass(aClass: UClass) {
    val javaClass = aClass.javaPsi
    val containingClass = javaClass.containingClass
    if (containingClass != null && aClass.isStatic && javaClass.hasAnnotation(ORG_JUNIT_JUPITER_API_NESTED)) {
      val message = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.nested.class.descriptor")
      val fixes = createModifierQuickfixes(aClass, modifierRequest(JvmModifier.STATIC, shouldBePresent = false)) ?: return
      holder.registerUProblem(aClass, message, *fixes)
    }
  }

  private val dataPoint = AnnotatedSignatureProblem(
    annotations = listOf(ORG_JUNIT_EXPERIMENTAL_THEORIES_DATAPOINT, ORG_JUNIT_EXPERIMENTAL_THEORIES_DATAPOINTS),
    shouldBeStatic = true,
    validVisibility = { UastVisibility.PUBLIC },
  )

  private val ruleSignatureProblem = AnnotatedSignatureProblem(
    annotations = listOf(ORG_JUNIT_RULE),
    shouldBeStatic = false,
    shouldBeSubTypeOf = listOf(ORG_JUNIT_RULES_TEST_RULE, ORG_JUNIT_RULES_METHOD_RULE),
    validVisibility = { UastVisibility.PUBLIC }
  )

  private val classRuleSignatureProblem = AnnotatedSignatureProblem(
    annotations = listOf(ORG_JUNIT_CLASS_RULE),
    shouldBeStatic = true,
    shouldBeSubTypeOf = listOf(ORG_JUNIT_RULES_TEST_RULE),
    validVisibility = { UastVisibility.PUBLIC }
  )

  private val beforeAfterProblem = AnnotatedSignatureProblem(
    annotations = listOf(ORG_JUNIT_BEFORE, ORG_JUNIT_AFTER),
    shouldBeStatic = false,
    shouldBeVoidType = true,
    validVisibility = { UastVisibility.PUBLIC },
    validParameters = { method -> method.uastParameters.filter { MetaAnnotationUtil.isMetaAnnotated(it, ignorableAnnotations) } }
  )

  private val beforeAfterEachProblem = AnnotatedSignatureProblem(
    annotations = listOf(ORG_JUNIT_JUPITER_API_BEFORE_EACH, ORG_JUNIT_JUPITER_API_AFTER_EACH),
    shouldBeStatic = false,
    shouldBeVoidType = true,
    validVisibility = ::notPrivate,
    validParameters = { method ->
      if (method.uastParameters.isEmpty()) emptyList()
      else if (method.hasParameterResolver()) method.uastParameters
      else method.uastParameters.filter {
        it.type.canonicalText == ORG_JUNIT_JUPITER_API_TEST_INFO ||
        it.type.canonicalText == ORG_JUNIT_JUPITER_API_REPETITION_INFO ||
        it.type.canonicalText == ORG_JUNIT_JUPITER_API_TEST_REPORTER ||
        MetaAnnotationUtil.isMetaAnnotated(it, ignorableAnnotations)
      }
    }
  )

  private val beforeAfterClassProblem = AnnotatedSignatureProblem(
    annotations = listOf(ORG_JUNIT_BEFORE_CLASS, ORG_JUNIT_AFTER_CLASS),
    shouldBeStatic = true,
    shouldBeVoidType = true,
    validVisibility = { UastVisibility.PUBLIC },
    validParameters = { method -> method.uastParameters.filter { MetaAnnotationUtil.isMetaAnnotated(it, ignorableAnnotations) } }
  )

  private val beforeAfterAllProblem = AnnotatedSignatureProblem(
    annotations = listOf(ORG_JUNIT_JUPITER_API_BEFORE_ALL, ORG_JUNIT_JUPITER_API_AFTER_ALL),
    shouldBeInTestInstancePerClass = true,
    shouldBeStatic = true,
    shouldBeVoidType = true,
    validVisibility = ::notPrivate,
    validParameters = { method ->
      if (method.uastParameters.isEmpty()) emptyList()
      else if (method.hasParameterResolver()) method.uastParameters
      else method.uastParameters.filter {
        it.type.canonicalText == ORG_JUNIT_JUPITER_API_TEST_INFO || MetaAnnotationUtil.isMetaAnnotated(it, ignorableAnnotations)
      }
    }
  )

  private val junit4TestProblem = AnnotatedSignatureProblem(
    annotations = listOf(ORG_JUNIT_TEST),
    shouldBeStatic = false,
    shouldBeVoidType = true,
    validVisibility = { UastVisibility.PUBLIC },
    validParameters = { method -> method.uastParameters.filter { MetaAnnotationUtil.isMetaAnnotated(it, ignorableAnnotations) } }
  )

  private val junit5TestProblem = AnnotatedSignatureProblem(
    annotations = listOf(ORG_JUNIT_JUPITER_API_TEST),
    shouldBeStatic = false,
    shouldBeVoidType = true,
    validVisibility = ::notPrivate,
    validParameters = { method ->
      if (method.uastParameters.isEmpty()) emptyList()
      else if (MetaAnnotationUtil.isMetaAnnotated(method.javaPsi, listOf(
          ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS_SOURCE))) null // handled in parameterized test check
      else if (method.hasParameterResolver()) method.uastParameters
      else method.uastParameters.filter {
        it.type.canonicalText == ORG_JUNIT_JUPITER_API_TEST_INFO ||
        it.type.canonicalText == ORG_JUNIT_JUPITER_API_TEST_REPORTER ||
        MetaAnnotationUtil.isMetaAnnotated(it, ignorableAnnotations)
      }
    }
  )

  private fun notPrivate(method: UDeclaration): UastVisibility? =
    if (method.visibility == UastVisibility.PRIVATE) UastVisibility.PUBLIC else null

  private fun UMethod.hasParameterResolver(): Boolean {
    val sourcePsi = this.sourcePsi ?: return false
    val alternatives = UastFacade.convertToAlternatives(sourcePsi, arrayOf(UMethod::class.java))
    val extension = alternatives.mapNotNull { it.javaPsi.containingClass }.flatMap {
      MetaAnnotationUtil.findMetaAnnotationsInHierarchy(it, listOf(ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH)).asSequence()
    }.firstOrNull()?.findAttributeValue("value")?.toUElement() ?: return false
    if (extension is UClassLiteralExpression) return InheritanceUtil.isInheritor(extension.type,
                                                                                 ORG_JUNIT_JUPITER_API_EXTENSION_PARAMETER_RESOLVER)
    if (extension is UCallExpression && extension.kind == UastCallKind.NESTED_ARRAY_INITIALIZER) return extension.valueArguments.any {
      it is UClassLiteralExpression && InheritanceUtil.isInheritor(it.type, ORG_JUNIT_JUPITER_API_EXTENSION_PARAMETER_RESOLVER)
    }
    return false
  }

  private fun checkMalformedExtension(field: UField) {
    val javaField = field.javaPsi?.let { it as? PsiField } ?: return
    val type = javaField.type
    if (javaField.hasAnnotation(ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION)) {
      if (!type.isInheritorOf(ORG_JUNIT_JUPITER_API_EXTENSION)) {
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.extension.registration.descriptor",
          javaField.type.canonicalText, ORG_JUNIT_JUPITER_API_EXTENSION
        )
        holder.registerUProblem(field, message)
      }
      else if (!field.isStatic && (type.isInheritorOf(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_ALL_CALLBACK)
                                   || type.isInheritorOf(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_ALL_CALLBACK))) {
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.extension.class.level.descriptor", javaField.type.presentableText
        )
        val fixes = createModifierQuickfixes(field, modifierRequest(JvmModifier.STATIC, shouldBePresent = true)) ?: return
        holder.registerUProblem(field, message, *fixes)
      }
    }
  }

  private fun UMethod.isNoArg(): Boolean = uastParameters.isEmpty() || uastParameters.all { param ->
    param.javaPsi?.let { it as? PsiParameter }?.let { AnnotationUtil.isAnnotated(it, ignorableAnnotations, 0) } == true
  }

  private fun checkSuspendFunction(method: UMethod): Boolean {
    return if (method.lang == Language.findLanguageByID("kotlin") && method.javaPsi.modifierList.text.contains("suspend")) {
      val message = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.suspend.function.descriptor")
      holder.registerUProblem(method, message)
      true
    } else false
  }

  private fun checkJUnit3Test(method: UMethod) {
    val sourcePsi = method.sourcePsi ?: return
    val alternatives = UastFacade.convertToAlternatives(sourcePsi, arrayOf(UMethod::class.java))
    val javaMethod = alternatives.firstOrNull { it.isStatic } ?: alternatives.firstOrNull() ?: return
    if (method.isConstructor) return
    if (!TestUtils.isJUnit3TestMethod(javaMethod.javaPsi)) return
    val containingClass = method.javaPsi.containingClass ?: return
    if (AnnotationUtil.isAnnotated(containingClass, TestUtils.RUN_WITH, AnnotationUtil.CHECK_HIERARCHY)) return
    if (checkSuspendFunction(method)) return
    if (PsiType.VOID != method.returnType || method.visibility != UastVisibility.PUBLIC || javaMethod.isStatic || !method.isNoArg()) {
      val message = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.method.no.arg.void.descriptor", "public", "non-static")
      return holder.registerUProblem(method, message, MethodSignatureQuickfix(method.name, false, newVisibility = JvmModifier.PUBLIC))
    }
  }

  private fun checkedMalformedSetupTeardown(method: UMethod) {
    if ("setUp" != method.name && "tearDown" != method.name) return
    if (!InheritanceUtil.isInheritor(method.javaPsi.containingClass, JUNIT_FRAMEWORK_TEST_CASE)) return
    val sourcePsi = method.sourcePsi ?: return
    if (checkSuspendFunction(method)) return
    val alternatives = UastFacade.convertToAlternatives(sourcePsi, arrayOf(UMethod::class.java))
    val javaMethod = alternatives.firstOrNull { it.isStatic } ?: alternatives.firstOrNull() ?: return
    if (PsiType.VOID != method.returnType || method.visibility == UastVisibility.PRIVATE || javaMethod.isStatic || !method.isNoArg()) {
      val message = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.method.no.arg.void.descriptor", "non-private", "non-static")
      val quickFix = MethodSignatureQuickfix(
        method.name, newVisibility = JvmModifier.PUBLIC, makeStatic = false, shouldBeVoidType = true, inCorrectParams = emptyMap()
      )
      return holder.registerUProblem(method, message, quickFix)
    }
  }

  private fun checkSuite(method: UMethod) {
    if ("suite" != method.name) return
    if (!InheritanceUtil.isInheritor(method.javaPsi.containingClass, JUNIT_FRAMEWORK_TEST_CASE)) return
    val sourcePsi = method.sourcePsi ?: return
    if (checkSuspendFunction(method)) return
    val alternatives = UastFacade.convertToAlternatives(sourcePsi, arrayOf(UMethod::class.java))
    val javaMethod = alternatives.firstOrNull { it.isStatic } ?: alternatives.firstOrNull() ?: return
    if (method.visibility == UastVisibility.PRIVATE || !javaMethod.isStatic || !method.isNoArg()) {
      val message = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.method.no.arg.descriptor", "non-private", "static")
      val quickFix = MethodSignatureQuickfix(
        method.name, newVisibility = JvmModifier.PUBLIC, makeStatic = true, shouldBeVoidType = false, inCorrectParams = emptyMap()
      )
      return holder.registerUProblem(method, message, quickFix)
    }
  }

  private fun checkIllegalCombinedAnnotations(decl: UDeclaration) {
    val javaPsi = decl.javaPsi as? PsiModifierListOwner ?: return
    val annotatedTest = NON_COMBINED_TEST.filter { MetaAnnotationUtil.isMetaAnnotated(javaPsi, listOf(it)) }
    if (annotatedTest.size > 1) {
      val last = annotatedTest.last().substringAfterLast('.')
      val annText = annotatedTest.dropLast(1).joinToString { "'@${it.substringAfterLast('.')}'" }
      val message = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.test.combination.descriptor", annText, last)
      return holder.registerUProblem(decl, message)
    }
    else if (annotatedTest.size == 1 && annotatedTest.first() != ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST) {
      val annotatedArgSource = PARAMETERIZED_SOURCES.filter { MetaAnnotationUtil.isMetaAnnotated(javaPsi, listOf(it)) }
      if (annotatedArgSource.isNotEmpty()) {
        val testAnnText = annotatedTest.first().substringAfterLast('.')
        val argAnnText = annotatedArgSource.joinToString { "'@${it.substringAfterLast('.')}'" }
        val message = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.test.combination.descriptor", argAnnText, testAnnText)
        return holder.registerUProblem(decl, message)
      }
    }
  }

  private fun checkRepeatedTestNonPositive(method: UMethod) {
    val repeatedAnno = method.findAnnotation(ORG_JUNIT_JUPITER_API_REPEATED_TEST) ?: return
    val repeatedNumber = repeatedAnno.findDeclaredAttributeValue("value") ?: return
    val repeatedSrcPsi = repeatedNumber.sourcePsi ?: return
    val constant = repeatedNumber.evaluate()
    if (constant is Int && constant <= 0) {
      holder.registerProblem(repeatedSrcPsi, JvmAnalysisBundle.message("jvm.inspections.junit.malformed.repetition.number.descriptor"))
    }
  }

  private fun checkMalformedParameterized(method: UMethod) {
    if (!MetaAnnotationUtil.isMetaAnnotated(method.javaPsi, listOf(ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST))) return
    val usedSourceAnnotations = MetaAnnotationUtil.findMetaAnnotations(method.javaPsi, SOURCE_ANNOTATIONS).toList()
    checkConflictingSourceAnnotations(usedSourceAnnotations.associateWith { it.qualifiedName }, method)
    usedSourceAnnotations.forEach {
      when (it.qualifiedName) {
        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE -> checkMethodSource(method, it)
        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_VALUE_SOURCE -> checkValuesSource(method, it)
        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ENUM_SOURCE -> checkEnumSource(method, it)
        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE -> checkCsvSource(it)
        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_NULL_SOURCE -> checkNullSource(method, it)
        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_EMPTY_SOURCE -> checkEmptySource(method, it)
        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_NULL_AND_EMPTY_SOURCE -> {
          checkNullSource(method, it)
          checkEmptySource(method, it)
        }
      }
    }
  }

  private fun checkConflictingSourceAnnotations(annMap: Map<PsiAnnotation, @NlsSafe String?>, method: UMethod) {
    val singleParameterProviders = annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ENUM_SOURCE) ||
                                   annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_VALUE_SOURCE) ||
                                   annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_NULL_SOURCE) ||
                                   annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_EMPTY_SOURCE) ||
                                   annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_NULL_AND_EMPTY_SOURCE)

    val multipleParametersProvider = annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE)
                                     || annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE)
                                     || annMap.containsValue(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_SOURCE)

    if (!multipleParametersProvider && !singleParameterProviders && hasCustomProvider(annMap)) return
    if (!multipleParametersProvider) {
      val message = if (!singleParameterProviders) {
        JvmAnalysisBundle.message("jvm.inspections.junit.malformed.param.no.sources.are.provided.descriptor")
      }
      else if (hasMultipleParameters(method.javaPsi)) {
        JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.param.multiple.parameters.are.not.supported.by.this.source.descriptor")
      }
      else return
      holder.registerUProblem(method, message)
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
      val message = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.param.method.source.unresolved.descriptor",
                                              sourceProviderName)
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
      val message = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.param.method.source.static.descriptor",
                                              providerName)
      holder.registerProblem(place, message, *quickFixes)
    }
    else if (sourceProvider.uastParameters.isNotEmpty() && !classHasParameterResolverField(containingClass)) {
      val message = JvmAnalysisBundle.message(
        "jvm.inspections.junit.malformed.param.method.source.no.params.descriptor", providerName)
      holder.registerProblem(place, message)
    }
    else {
      val componentType = getComponentType(sourceProvider.returnType, method.javaPsi)
      if (componentType == null) {
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.param.method.source.return.type.descriptor", providerName)
        holder.registerProblem(place, message)
      }
      else if (hasMultipleParameters(method.javaPsi)
               && !InheritanceUtil.isInheritor(componentType, ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ARGUMENTS)
               && !componentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
               && !componentType.deepComponentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
      ) {
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.param.wrapped.in.arguments.descriptor")
        holder.registerProblem(place, message)
      }
    }
  }

  private fun classHasParameterResolverField(aClass: PsiClass?): Boolean {
    if (aClass == null) return false
    if (aClass.isInterface) return false
    return aClass.fields.any { field ->
      AnnotationUtil.isAnnotated(field, ORG_JUNIT_JUPITER_API_EXTENSION_REGISTER_EXTENSION, 0) &&
      field.type.isInheritorOf(ORG_JUNIT_JUPITER_API_EXTENSION_PARAMETER_RESOLVER)
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
    return containingClass != null && method.parameterList.parameters.count {
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
        "jvm.inspections.junit.malformed.param.empty.source.cannot.provide.argument.descriptor",
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
        "jvm.inspections.junit.malformed.param.null.source.cannot.provide.argument.no.params.descriptor",
        StringUtil.getShortName(sourceName))
    }
    else {
      JvmAnalysisBundle.message(
        "jvm.inspections.junit.malformed.param.null.source.cannot.provide.argument.too.many.params.descriptor",
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
          "jvm.inspections.junit.malformed.param.method.source.assignable.descriptor",
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
      JvmAnalysisBundle.message("jvm.inspections.junit.malformed.param.no.value.source.is.defined.descriptor")
    }
    else if (attributesNumber > 1) {
      JvmAnalysisBundle.message(
        "jvm.inspections.junit.malformed.param.exactly.one.type.of.input.must.be.provided.descriptor")
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
              JvmAnalysisBundle.message("jvm.inspections.junit.malformed.param.unresolved.enum.descriptor")
            }
            else if (!definedConstants.add(value)) {
              JvmAnalysisBundle.message("jvm.inspections.junit.malformed.param.duplicated.enum.descriptor")
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
          val message = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.param.file.source.descriptor", attributeValue.text)
          holder.registerProblem(ref.element, message, *ref.quickFixes)
        }
      }
    }
  }

  private fun PsiAnnotation.nestedAttributeValues(value: String) = findAttributeValue(value)?.nestedValues()

  private fun PsiAnnotationMemberValue.nestedValues(): List<PsiAnnotationMemberValue> {
    return if (this is PsiArrayInitializerMemberValue) initializers.flatMap { it.nestedValues() } else listOf(this)
  }

  class AnnotatedSignatureProblem(
    private val annotations: List<String>,
    private val shouldBeStatic: Boolean,
    private val shouldBeInTestInstancePerClass: Boolean = false,
    private val shouldBeVoidType: Boolean? = null,
    private val shouldBeSubTypeOf: List<String>? = null,
    private val validVisibility: ((UDeclaration) -> UastVisibility?)? = null,
    private val validParameters: ((UMethod) -> List<UParameter>?)? = null,
  ) {
    private fun modifierProblems(
      validVisibility: UastVisibility?, decVisibility: UastVisibility, isStatic: Boolean, isInstancePerClass: Boolean
    ): List<@NlsSafe String> {
      val problems = mutableListOf<String>()
      if (shouldBeInTestInstancePerClass) { if (!isStatic && !isInstancePerClass) problems.add("static") }
      else if (shouldBeStatic && !isStatic) problems.add("static")
      else if (!shouldBeStatic && isStatic) problems.add("non-static")
      if (validVisibility != null && validVisibility != decVisibility) problems.add(validVisibility.text)
      return problems
    }

    fun report(holder: ProblemsHolder, element: UField) {
      val javaPsi = element.javaPsi as? PsiField ?: return
      val annotation = annotations.firstOrNull { MetaAnnotationUtil.isMetaAnnotated(javaPsi, annotations) } ?: return
      val visibility = validVisibility?.invoke(element)
      val problems = modifierProblems(visibility, element.visibility, element.isStatic, false)
      if (shouldBeVoidType == true && element.type != PsiType.VOID) {
        return holder.fieldTypeProblem(element, visibility, annotation, problems, PsiType.VOID.name)
      }
      if (shouldBeSubTypeOf?.any { InheritanceUtil.isInheritor(element.type, it) } == false) {
        return holder.fieldTypeProblem(element, visibility, annotation, problems, shouldBeSubTypeOf.first())
      }
      if (problems.isNotEmpty()) return holder.fieldModifierProblem(element, visibility, annotation, problems)
    }

    private fun ProblemsHolder.fieldModifierProblem(
      element: UField, visibility: UastVisibility?, annotation: String, problems: List<@NlsSafe String>
    ) {
      val message = if (problems.size == 1) {
        JvmAnalysisBundle.message("jvm.inspections.junit.malformed.annotated.field.single.descriptor",
                                  annotation.substringAfterLast('.'), problems.first()
        )
      } else {
        JvmAnalysisBundle.message("jvm.inspections.junit.malformed.annotated.field.double.descriptor",
                                  annotation.substringAfterLast('.'), problems.first(), problems.last()
        )
      }
      reportFieldProblem(message, element, visibility)
    }

    private fun ProblemsHolder.fieldTypeProblem(
      element: UField, visibility: UastVisibility?, annotation: String, problems: List<@NlsSafe String>, type: String
    ) {
      if (problems.isEmpty()) {
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.field.typed.descriptor", annotation.substringAfterLast('.'), type)
        registerUProblem(element, message)
      }
      else if (problems.size == 1) {
        val message = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.annotated.field.single.typed.descriptor",
                                  annotation.substringAfterLast('.'), problems.first(), type
        )
        reportFieldProblem(message, element, visibility)
      } else {
        val message = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.annotated.field.double.typed.descriptor",
                                  annotation.substringAfterLast('.'), problems.first(), problems.last(), type
        )
        reportFieldProblem(message, element, visibility)
      }
    }

    private fun ProblemsHolder.reportFieldProblem(message: @InspectionMessage String, element: UField, visibility: UastVisibility?) {
      val quickFix = FieldSignatureQuickfix(element.name, shouldBeStatic, visibilityToModifier[visibility])
      return registerUProblem(element, message, quickFix)
    }

    fun report(holder: ProblemsHolder, element: UMethod) {
      val javaPsi = element.javaPsi as? PsiMethod ?: return
      val sourcePsi = element.sourcePsi ?: return
      val annotation = annotations.firstOrNull { AnnotationUtil.isAnnotated(javaPsi, it, AnnotationUtil.CHECK_HIERARCHY) } ?: return
      val alternatives = UastFacade.convertToAlternatives(sourcePsi, arrayOf(UMethod::class.java))
      val elementIsStatic = alternatives.any { it.isStatic }
      val visibility = validVisibility?.invoke(element)
      val params = validParameters?.invoke(element)
      val problems = modifierProblems(
        visibility, element.visibility, elementIsStatic, javaPsi.containingClass?.let { cls -> TestUtils.testInstancePerClass(cls) } == true
      )
      if (element.lang == Language.findLanguageByID("kotlin") && element.javaPsi.modifierList.text.contains("suspend")) {
        val message = JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.suspend.function.descriptor",
          annotation.substringAfterLast('.')
        )
        return holder.registerUProblem(element, message)
      }
      if (params != null && params.size != element.uastParameters.size) {
        if (shouldBeVoidType == true && element.returnType != PsiType.VOID) {
          return holder.methodParameterTypeProblem(element, visibility, annotation, problems, PsiType.VOID.name, params)
        }
        if (shouldBeSubTypeOf?.any { InheritanceUtil.isInheritor(element.returnType, it) } == false) {
          return holder.methodParameterTypeProblem(element, visibility, annotation, problems, shouldBeSubTypeOf.first(), params)
        }
        return holder.methodParameterProblem(element, visibility, annotation, problems, params)
      }
      if (shouldBeVoidType == true && element.returnType != PsiType.VOID) {
        return holder.methodTypeProblem(element, visibility, annotation, problems, PsiType.VOID.name)
      }
      if (shouldBeSubTypeOf?.any { InheritanceUtil.isInheritor(element.returnType, it) } == false) {
        return holder.methodTypeProblem(element, visibility, annotation, problems, shouldBeSubTypeOf.first())
      }
      if (problems.isNotEmpty()) return holder.methodModifierProblem(element, visibility, annotation, problems)
    }

    private fun ProblemsHolder.methodParameterProblem(
      element: UMethod, visibility: UastVisibility?, annotation: String, problems: List<@NlsSafe String>, parameters: List<UParameter>
    ) {
      val invalidParams = element.uastParameters.toMutableList().apply { removeAll(parameters) }
      val message = when {
        problems.isEmpty() && invalidParams.size == 1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.param.single.descriptor",
          annotation.substringAfterLast('.'), invalidParams.first().name
        )
        problems.isEmpty() && invalidParams.size > 1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.param.double.descriptor",
          annotation.substringAfterLast('.'), invalidParams.joinToString { "'$it'" }, invalidParams.last().name
        )
        problems.size == 1 && invalidParams.size == 1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.single.param.single.descriptor",
          annotation.substringAfterLast('.'), problems.first(), invalidParams.first().name
        )
        problems.size == 1 && invalidParams.size > 1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.single.param.double.descriptor",
          annotation.substringAfterLast('.'), problems.first(), invalidParams.joinToString { "'$it'" },
          invalidParams.last().name
        )
        problems.size == 2 && invalidParams.size == 1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.double.param.single.descriptor",
          annotation.substringAfterLast('.'), problems.first(), problems.last(), invalidParams.first().name
        )
        problems.size == 2 && invalidParams.size > 1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.double.param.double.descriptor",
          annotation.substringAfterLast('.'), problems.first(), problems.last(), invalidParams.joinToString { "'$it'" },
          invalidParams.last().name
        )
        else -> error("Non valid problem.")
      }
      reportMethodProblem(message, element, visibility)
    }

    private fun ProblemsHolder.methodParameterTypeProblem(
      element: UMethod, visibility: UastVisibility?, annotation: String, problems: List<@NlsSafe String>, type: String,
      parameters: List<UParameter>
    ) {
      val invalidParams = element.uastParameters.toMutableList().apply { removeAll(parameters) }
      val message = when {
        problems.isEmpty() && invalidParams.size == 1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.typed.param.single.descriptor",
          annotation.substringAfterLast('.'), type, invalidParams.first().name
        )
        problems.isEmpty() && invalidParams.size > 1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.typed.param.double.descriptor",
          annotation.substringAfterLast('.'), type, invalidParams.joinToString { "'$it'" }, invalidParams.last().name
        )
        problems.size == 1 && invalidParams.size == 1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.single.typed.param.single.descriptor",
          annotation.substringAfterLast('.'), problems.first(), type, invalidParams.first().name
        )
        problems.size == 1 && invalidParams.size > 1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.single.typed.param.double.descriptor",
          annotation.substringAfterLast('.'), problems.first(), type, invalidParams.joinToString { "'$it'" },
          invalidParams.last().name
        )
        problems.size == 2 && invalidParams.size == 1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.double.typed.param.single.descriptor",
          annotation.substringAfterLast('.'), problems.first(), problems.last(), type, invalidParams.first().name
        )
        problems.size == 2 && invalidParams.size > 1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.double.typed.param.double.descriptor",
          annotation.substringAfterLast('.'), problems.first(), problems.last(), type, invalidParams.joinToString { "'$it'" },
          invalidParams.last().name
        )
        else -> error("Non valid problem.")
      }
      reportMethodProblem(message, element, visibility)
    }

    private fun ProblemsHolder.methodTypeProblem(
      element: UMethod, visibility: UastVisibility?, annotation: String, problems: List<@NlsSafe String>, type: String
    ) {
      val message = if (problems.isEmpty()) {
        JvmAnalysisBundle.message(
          "jvm.inspections.junit.malformed.annotated.method.typed.descriptor", annotation.substringAfterLast('.'), type
        )
      } else if (problems.size == 1) {
        JvmAnalysisBundle.message("jvm.inspections.junit.malformed.annotated.method.single.typed.descriptor",
                                  annotation.substringAfterLast('.'), problems.first(), type
        )
      } else {
        JvmAnalysisBundle.message("jvm.inspections.junit.malformed.annotated.method.double.typed.descriptor",
                                  annotation.substringAfterLast('.'), problems.first(), problems.last(), type
        )
      }
      reportMethodProblem(message, element, visibility)
    }

    private fun ProblemsHolder.methodModifierProblem(
      element: UMethod, visibility: UastVisibility?, annotation: String, problems: List<@NlsSafe String>
    ) {
      val message = if (problems.size == 1) {
        JvmAnalysisBundle.message("jvm.inspections.junit.malformed.annotated.method.single.descriptor",
                                  annotation.substringAfterLast('.'), problems.first()
        )
      } else {
        JvmAnalysisBundle.message("jvm.inspections.junit.malformed.annotated.method.double.descriptor",
                                  annotation.substringAfterLast('.'), problems.first(), problems.last()
        )
      }
      reportMethodProblem(message, element, visibility)
    }

    private fun ProblemsHolder.reportMethodProblem(message: @InspectionMessage String,
                                                   element: UMethod,
                                                   visibility: UastVisibility? = null,
                                                   params: List<UParameter>? = null) {
      val quickFix = MethodSignatureQuickfix(
        element.name, shouldBeStatic, shouldBeVoidType, visibilityToModifier[visibility],
        params?.associate { it.name to it.type } ?: emptyMap()
      )
      return registerUProblem(element, message, quickFix)
    }
  }

  class FieldSignatureQuickfix(
    private val name: @NlsSafe String,
    private val makeStatic: Boolean,
    private val newVisibility: JvmModifier? = null
  ) : LocalQuickFix {
    override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.fix.field.signature")

    override fun getName(): String = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.fix.field.signature.descriptor", name)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val containingFile = descriptor.psiElement.containingFile ?: return
      val javaDeclaration = getUParentForIdentifier(descriptor.psiElement)?.let { it as? UField }?.javaPsi ?: return
      val declPtr = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(javaDeclaration)
      if (newVisibility != null) {
        declPtr.element?.let { it as? JvmModifiersOwner }?.let { jvmMethod ->
          createModifierActions(jvmMethod, modifierRequest(newVisibility, true)).forEach {
            it.invoke(project, null, containingFile)
          }
        }
      }
      declPtr.element?.let { it as? JvmModifiersOwner }?.let { jvmMethod ->
        createModifierActions(jvmMethod, modifierRequest(JvmModifier.STATIC, makeStatic)).forEach {
          it.invoke(project, null, containingFile)
        }
      }
    }
  }

  class MethodSignatureQuickfix(
    private val name: @NlsSafe String,
    private val makeStatic: Boolean,
    private val shouldBeVoidType: Boolean? = null,
    private val newVisibility: JvmModifier? = null,
    @SafeFieldForPreview private val inCorrectParams: Map<String, JvmType>? = null
  ) : LocalQuickFix {
    override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.fix.method.signature")

    override fun getName(): String = JvmAnalysisBundle.message("jvm.inspections.junit.malformed.fix.method.signature.descriptor", name)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val containingFile = descriptor.psiElement.containingFile ?: return
      val javaDeclaration = getUParentForIdentifier(descriptor.psiElement)?.let { it as? UMethod }?.javaPsi ?: return
      val declPtr = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(javaDeclaration)
      if (shouldBeVoidType == true) {
        declPtr.element?.let { jvmMethod ->
          createChangeTypeActions(jvmMethod, typeRequest(JvmPrimitiveTypeKind.VOID.name, emptyList())).forEach {
            it.invoke(project, null, containingFile)
          }
        }
      }
      if (newVisibility != null) {
        declPtr.element?.let { jvmMethod ->
          createModifierActions(jvmMethod, modifierRequest(newVisibility, true)).forEach {
            it.invoke(project, null, containingFile)
          }
        }
      }
      if (inCorrectParams != null) {
        declPtr.element?.let { jvmMethod ->
          createChangeParametersActions(jvmMethod, setMethodParametersRequest(inCorrectParams.entries)).forEach {
            it.invoke(project, null, containingFile)
          }
        }
      }
      declPtr.element?.let { jvmMethod ->
        createModifierActions(jvmMethod, modifierRequest(JvmModifier.STATIC, makeStatic)).forEach {
          it.invoke(project, null, containingFile)
        }
      }
    }
  }

  companion object {
    private const val TEST_INSTANCE_PER_CLASS = "@org.junit.jupiter.api.TestInstance(TestInstance.Lifecycle.PER_CLASS)"
    private const val METHOD_SOURCE_RETURN_TYPE = "java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>"

    private val visibilityToModifier = mapOf(
      UastVisibility.PUBLIC to JvmModifier.PUBLIC,
      UastVisibility.PROTECTED to JvmModifier.PROTECTED,
      UastVisibility.PRIVATE to JvmModifier.PRIVATE,
      UastVisibility.PACKAGE_LOCAL to JvmModifier.PACKAGE_LOCAL
    )

    private val NON_COMBINED_TEST = listOf(
      ORG_JUNIT_JUPITER_API_TEST,
      ORG_JUNIT_JUPITER_API_TEST_FACTORY,
      ORG_JUNIT_JUPITER_API_REPEATED_TEST,
      ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST
    )

    private val PARAMETERIZED_SOURCES = listOf(
      ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE,
      ORG_JUNIT_JUPITER_PARAMS_PROVIDER_VALUE_SOURCE,
      ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ENUM_SOURCE,
      ORG_JUNIT_JUPITER_PARAMS_PROVIDER_CSV_FILE_SOURCE,
      ORG_JUNIT_JUPITER_PARAMS_PROVIDER_NULL_SOURCE,
      ORG_JUNIT_JUPITER_PARAMS_PROVIDER_EMPTY_SOURCE,
      ORG_JUNIT_JUPITER_PARAMS_PROVIDER_NULL_AND_EMPTY_SOURCE
    )
  }
}
