// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink

import com.intellij.codeInsight.AnnotationTargetUtil
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInspection.restriction.AnnotationContext
import com.intellij.codeInspection.restriction.RestrictionInfo.RestrictionInfoKind
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.PsiImplUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.psiutils.MethodMatcher
import org.jetbrains.uast.*
import kotlin.streams.asSequence


internal class CustomContext(val target: PsiElement, val place: PsiElement?, val targetClass: PsiClass?)

class TaintValueFactory(private val myConfiguration: UntaintedConfiguration) {
  private val JAVAX_ANNOTATION_UNTAINTED = "javax.annotation.Untainted"
  private val myTaintedAnnotations: MutableSet<String> = HashSet()
  private val myUnTaintedAnnotations: MutableSet<String> = HashSet()
  private val customFactories: MutableList<(CustomContext) -> TaintValue?> = ArrayList()
  private val customReturnFactories:  MutableList<(PsiMethod, PsiClass?) -> ReturnFactoriesResult?> = ArrayList()
  private val customQualifierCleaner: MutableList<(UCallExpression)->Boolean> = ArrayList()
  init {
    myTaintedAnnotations.addAll(myConfiguration.taintedAnnotations.filterNotNull())
    myUnTaintedAnnotations.addAll(myConfiguration.unTaintedAnnotations.filterNotNull())

    addReturnFactory(fromMethodResult(methodNames = myConfiguration.methodNames,
                                      methodClass = myConfiguration.methodClass,
                                      targetValue = TaintValue.UNTAINTED))

    add(fromField(myConfiguration))
  }

  fun addQualifierCleaner(methodNames: List<String?>,
                          methodClass: List<String?>,
                          methodParams: List<String?>){
    val fromMethodResult = fromMethodResult(methodNames, methodClass, TaintValue.UNTAINTED)
    customQualifierCleaner.add { element ->
      val psiMethod = element.resolve() ?: return@add false
      val factoriesResult = fromMethodResult.invoke(psiMethod, null)
      if (factoriesResult == null || factoriesResult.taintValue != TaintValue.UNTAINTED) {
        return@add false
      }
      val className = factoriesResult.className
      val index = methodClass.indexOf(className)
      if (index < 0 || methodParams.size <= index) {
        return@add false
      }
      val params = methodParams[index]
      if (params == null) {
        return@add false
      }
      val expectedParams = params.split(",")
      val valueArguments = element.valueArguments
      if (valueArguments.size != expectedParams.size) {
        return@add false
      }
      for (i in valueArguments.indices) {
        val uExpression = valueArguments[i]
        if (uExpression is ULiteralExpression) {
          val value = uExpression.value.toString()
          if (value == expectedParams[i]) {
            continue
          }
          else {
            return@add false
          }
        }
        else {
          return@add false
        }
      }
      return@add true
    }
  }

  fun addReturnFactory(customReturnFactory: (PsiMethod, PsiClass?) -> ReturnFactoriesResult?) {
    customReturnFactories.add(customReturnFactory)
  }

  fun add(customFactory: (PsiElement) -> TaintValue?) {
    customFactories.add(adapterToContext(customFactory))
  }

  internal fun addForContext(customFactory: (CustomContext) -> TaintValue?) {
    customFactories.add(customFactory)
  }

  private fun adapterToContext(previous: (PsiElement) -> TaintValue?): (CustomContext) -> TaintValue? {
    return {
      val target = it.target
      previous.invoke(target)
    }
  }

  private fun fromAnnotationOwner(annotationOwner: PsiAnnotationOwner?): TaintValue {
    if (annotationsIsEmpty()) {
      return TaintValue.UNKNOWN
    }
    if (annotationOwner == null) return TaintValue.UNKNOWN
    for (annotation in annotationOwner.annotations) {
      val value = fromAnnotation(annotation)
      if (value != null) return value
    }
    return if (annotationOwner !is PsiModifierListOwner) TaintValue.UNKNOWN else of(annotationOwner as PsiModifierListOwner)
  }

  private fun fromModifierListOwner(modifierListOwner: PsiModifierListOwner, allowSecond: Boolean): TaintValue {
    val annotationContext = AnnotationContext.fromModifierListOwner(modifierListOwner)
    return fromAnnotationContextInner(annotationContext, allowSecond)
  }

  fun fromAnnotationContext(context: AnnotationContext): TaintValue {
    return fromAnnotationContextInner(context, true)
  }

  private fun fromAnnotationContextInner(context: AnnotationContext, allowSecond: Boolean): TaintValue {
    val type = context.type
    var info = fromAnnotationOwner(type)
    if (info !== TaintValue.UNKNOWN) return info
    val owner = context.owner ?: return TaintValue.UNKNOWN
    info = fromAnnotationOwner(owner.modifierList)
    if (info !== TaintValue.UNKNOWN) return info
    info = fromExternalAnnotations(owner)
    if (info !== TaintValue.UNKNOWN) return info
    if (owner is PsiParameter) {
      info = of(owner)
      if (info !== TaintValue.UNKNOWN) return info
      if (owner.isVarArgs && type is PsiEllipsisType) {
        info = fromAnnotationOwner(type.componentType)
      }
    }
    else if (owner is PsiVariable) {
      val uLocal = owner.toUElement(ULocalVariable::class.java)
      if (uLocal != null) {
        val psi = uLocal.javaPsi
        if (psi is PsiAnnotationOwner) {
          info = fromAnnotationOwner(psi as PsiAnnotationOwner?)
        }
      }
    }
    if (info.kind != RestrictionInfoKind.KNOWN) {
      val customContext = CustomContext(owner, context.place, null)
      info = tryFromCustom(customContext) ?: info
      if (allowSecond) {
        info = context.secondaryItems().asSequence()
                 .flatMap { listOf(fromElementInner(CustomContext(it, context.place, null), false), fromAnnotationOwner(it.modifierList)) }
                 .filter { it != null && it !== TaintValue.UNKNOWN }
                 .firstOrNull() ?: info
      }
    }
    if (info == TaintValue.UNKNOWN) {
      val obj: Any = if (owner is PsiParameter) owner.declarationScope else owner
      val member = (obj as? PsiMember)
      if (member != null) {
        info = of(member)
      }
    }
    val toUElement = owner.toUElement()
    if (info == TaintValue.UNKNOWN && toUElement is UVariable) {
      return toUElement.uAnnotations
               .mapNotNull { fromUAnnotation(it) }
               .firstOrNull { it != TaintValue.UNKNOWN } ?: info
    }
    return info
  }

  private fun fromExternalAnnotations(owner: PsiModifierListOwner): TaintValue {
    if (annotationsIsEmpty()) {
      return TaintValue.UNKNOWN
    }
    val annotationsManager = ExternalAnnotationsManager.getInstance(owner.project)
    val annotations: Array<out PsiAnnotation>? = annotationsManager.findExternalAnnotations(owner)
    return annotations?.asSequence()
             ?.map { fromAnnotation(it) }
             ?.filterNotNull()
             ?.firstOrNull() ?: TaintValue.UNKNOWN
  }

  private fun of(annotationOwner: PsiModifierListOwner): TaintValue {
    if (annotationsIsEmpty()) {
      return TaintValue.UNKNOWN
    }
    val allNames = java.util.HashSet<String>()
    allNames.addAll(myUnTaintedAnnotations)
    allNames.addAll(myTaintedAnnotations)
    val annotation = AnnotationUtil.findAnnotationInHierarchy(annotationOwner, allNames, false)
                     ?: return TaintValue.UNKNOWN
    val value = fromAnnotation(annotation)
    return value ?: TaintValue.UNKNOWN
  }

  private fun of(member: PsiMember): TaintValue {
    var containingClass = member.containingClass
    while (containingClass != null) {
      val classInfo = fromAnnotationOwner(containingClass.modifierList)
      if (classInfo !== TaintValue.UNKNOWN) {
        return classInfo
      }
      containingClass = containingClass.containingClass
    }
    return TaintValue.UNKNOWN
  }

  private fun fromAnnotation(annotation: PsiAnnotation): TaintValue? {
    val annotationQualifiedName = annotation.qualifiedName
    val fromJsr = processJsr(annotationQualifiedName, annotation)
    if (fromJsr != null) return fromJsr
    if (myTaintedAnnotations.contains(annotationQualifiedName)) {
      return TaintValue.TAINTED
    }
    return if (myUnTaintedAnnotations.contains(annotationQualifiedName) && annotationQualifiedName != JAVAX_ANNOTATION_UNTAINTED) {
      TaintValue.UNTAINTED
    }
    else null
  }

  private fun fromUAnnotation(annotation: UAnnotation): TaintValue? {
    if (annotationsIsEmpty()) {
      return TaintValue.UNKNOWN
    }
    val annotationQualifiedName = annotation.qualifiedName
    val fromJsr = processUJsr(annotationQualifiedName, annotation)
    if (fromJsr != null) return fromJsr
    if (myTaintedAnnotations.contains(annotationQualifiedName)) {
      return TaintValue.TAINTED
    }
    return if (myUnTaintedAnnotations.contains(annotationQualifiedName) && annotationQualifiedName != JAVAX_ANNOTATION_UNTAINTED) {
      TaintValue.UNTAINTED
    }
    else null
  }

  private fun annotationsIsEmpty(): Boolean {
    return myTaintedAnnotations.isEmpty() && myUnTaintedAnnotations.isEmpty()
  }

  private fun processJsr(qualifiedName: String?, annotation: PsiAnnotation): TaintValue? {
    if (qualifiedName == null ||
        qualifiedName != JAVAX_ANNOTATION_UNTAINTED ||
        !myUnTaintedAnnotations.contains(JAVAX_ANNOTATION_UNTAINTED)) {
      return null
    }
    val whenAttribute = annotation.findAttributeValue("when") ?: return TaintValue.UNTAINTED
    return if (whenAttribute.textMatches("ALWAYS") || whenAttribute.textMatches(
        "javax.annotation.meta.When.ALWAYS")) TaintValue.UNTAINTED
    else null
  }

  private fun processUJsr(qualifiedName: String?, annotation: UAnnotation): TaintValue? {
    if (qualifiedName == null ||
        qualifiedName != JAVAX_ANNOTATION_UNTAINTED ||
        !myUnTaintedAnnotations.contains(JAVAX_ANNOTATION_UNTAINTED)) {
      return null
    }
    val whenAttribute = annotation.findAttributeValue("when") ?: return TaintValue.UNTAINTED
    return if ((whenAttribute.evaluate() as? Pair<*, *>)?.second.toString() == "ALWAYS") TaintValue.UNTAINTED else null
  }

  fun fromElement(target: PsiElement?, targetClass: PsiClass?): TaintValue? {
    if(target==null) return null
    return fromElementInner(CustomContext(target, null, targetClass) , true)
  }
  private fun fromElementInner(context: CustomContext?, allowSecond: Boolean): TaintValue? {
    if (context == null) return null
    val target = context.target
    val type = PsiUtil.getTypeByPsiElement(target) ?: return null
    if (target is PsiClass) return null
    var taintValue = tryFromCustom(context)
    if (taintValue != null) return taintValue
    if (target is PsiModifierListOwner) {
      taintValue = fromModifierListOwner(target, allowSecond)
      if (taintValue === TaintValue.UNKNOWN) taintValue = of(target)
      if (taintValue !== TaintValue.UNKNOWN) return taintValue
    }
    return fromAnnotationOwner(type)
  }

  private fun tryFromCustom(context: CustomContext): TaintValue? {
    val target = context.target
    if (target is PsiMethod) {
      val result = customReturnFactories.asSequence()
        .map { it.invoke(target, context.targetClass) }
        .filterNotNull()
        .reduceOrNull { acc, returnFactoriesResult -> acc.reduce(returnFactoriesResult) }
      if (result?.taintValue != null) {
        return result.taintValue
      }
    }
    return customFactories.asSequence()
      .map { it.invoke(context) }
      .find { it != null }
  }

  fun getAnnotation(): String? {
    return myConfiguration.firstAnnotation
  }

  fun getAnnotationTarget(project: Project, scope: GlobalSearchScope): Set<PsiAnnotation.TargetType> {
    val firstAnnotation = myConfiguration.firstAnnotation ?: return setOf()
    val annotationClass = JavaPsiFacade.getInstance(project).findClass(firstAnnotation, scope)
                          ?: return setOf()
    val targets = AnnotationTargetUtil.getAnnotationTargets(annotationClass)
    return targets ?: setOf()
  }


  fun getConfiguration(): UntaintedConfiguration {
    return myConfiguration
  }

  fun needToCleanQualifier(node: UCallExpression): Boolean {
    return customQualifierCleaner.any { it.invoke(node) }
  }

  companion object {

    private fun fromField(context: UntaintedConfiguration): (PsiElement) -> TaintValue? {
      return {
        var result: TaintValue? = null
        if (it is PsiField) {
          val fieldNamed: List<String?> = context.fieldNames
          val fieldClass = context.fieldClass
          for (i in fieldNamed.indices) {
            if (fieldNamed[i] == null || fieldNamed[i] != it.name) {
              continue
            }
            if (fieldClass.size <= i) {
              break
            }
            val containingClass: PsiClass = it.containingClass ?: continue
            val className = fieldClass[i] ?: continue
            if (className == containingClass.qualifiedName) {
              result = TaintValue.UNTAINTED
            }
          }
        }
        result
      }
    }

    fun fromParameters(methodNames: List<String?>,
                               methodClass: List<String?>,
                               methodParameterIndex: List<Int?>,
                               targetValue: TaintValue): (PsiElement) -> TaintValue? {
      val matchers: MutableList<MethodMatcher> = mutableListOf()
      val allMatcher = MethodMatcher()
      for (i in methodNames.indices) {
        if (i >= methodClass.size || i >= methodParameterIndex.size) {
          break
        }
        val cl = methodClass[i]
        val name = methodNames[i]
        if (cl == null || name == null) continue
        allMatcher.add(cl, name)

        matchers.add(MethodMatcher().also { it.add(cl, name) })
      }
      return fun(it: PsiElement): TaintValue? {
        if (it is PsiParameter && it.parent is PsiParameterList &&
            allMatcher.matches(it.parent.parent as? PsiMethod)) {
          val parameterIndex = PsiImplUtil.getParameterIndex(it, it.parent as PsiParameterList)
          for ((index, methodMatcher) in matchers.withIndex()) {
            if (methodParameterIndex.size > index &&
                methodParameterIndex[index] != null &&
                methodParameterIndex[index] == parameterIndex &&
                methodMatcher.matches(it.parent.parent as? PsiMethod)) {
              return targetValue
            }
          }
          return null
        }
        else {
          return null
        }
      }
    }

    fun fromMethodResult(methodNames: List<String?>,
                                 methodClass: List<String?>,
                                 targetValue: TaintValue): (PsiMethod, PsiClass?) -> ReturnFactoriesResult? {
      val matcher = MethodMatcher()
      for (i in methodNames.indices) {
        if (i >= methodClass.size) {
          break
        }
        val cl = methodClass[i]
        val name = methodNames[i]
        if (cl == null || name == null) continue
        matcher.add(cl, name)
      }
      return { method, clazz ->
        val index = matcher.find(method, clazz, true)
        if (index == -1) {
          null
        }
        else {
          ReturnFactoriesResult(targetValue, methodClass[index], methodNames[index], method)
        }
      }
    }
  }

  class ReturnFactoriesResult(val taintValue: TaintValue?, val className: String?, val regexpMethod: String?, val method: PsiMethod){
    fun reduce(next: ReturnFactoriesResult): ReturnFactoriesResult {
      if (taintValue == null) {
        return this
      }
      if (next.taintValue == null) {
        return next
      }
      if (className == next.className) {
        if (taintValue == next.taintValue) {
          return this
        }
        else {
          return ReturnFactoriesResult(null, regexpMethod, className, method)
        }
      }
      if (className == null) {
        return next
      }
      if (next.className == null) {
        return this
      }
      val thisClass = JavaPsiFacade.getInstance(method.getProject()).findClass(className, method.resolveScope)
      val nextClass = JavaPsiFacade.getInstance(method.getProject()).findClass(next.className, method.resolveScope)
      if (thisClass == null || nextClass == null) {
        return ReturnFactoriesResult(null, regexpMethod, className, method)
      }
      if (InheritanceUtil.isInheritorOrSelf(thisClass, nextClass, true)) {
        return this
      }
      if (InheritanceUtil.isInheritorOrSelf(nextClass, thisClass, true)) {
        return next
      }
      return ReturnFactoriesResult(null, regexpMethod, className, method)
    }
  }
}
