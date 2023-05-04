// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink

import com.intellij.codeInsight.AnnotationTargetUtil
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInspection.restriction.AnnotationContext
import com.intellij.codeInspection.restriction.RestrictionInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.psiutils.MethodMatcher
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.toUElement
import kotlin.streams.asSequence

class TaintValueFactory(private val myConfiguration: UntaintedConfiguration) {
  private val JAVAX_ANNOTATION_UNTAINTED = "javax.annotation.Untainted"
  private val myTaintedAnnotations: MutableSet<String> = HashSet()
  private val myUnTaintedAnnotations: MutableSet<String> = HashSet()
  private val customFactories: MutableList<(PsiElement) -> TaintValue?> = ArrayList()

  init {
    myTaintedAnnotations.addAll(myConfiguration.taintedAnnotations.filterNotNull())
    myUnTaintedAnnotations.addAll(myConfiguration.unTaintedAnnotations.filterNotNull())
    customFactories.add(fromField(myConfiguration))
    customFactories.add(fromMethod(myConfiguration))
  }


  fun add(customFactory: (PsiElement) -> TaintValue?) {
    customFactories.add(customFactory)
  }

  private fun fromAnnotationOwner(annotationOwner: PsiAnnotationOwner?): TaintValue {
    if (annotationOwner == null) return TaintValue.UNKNOWN
    for (annotation in annotationOwner.annotations) {
      val value = fromAnnotation(annotation)
      if (value != null) return value
    }
    return if (annotationOwner !is PsiModifierListOwner) TaintValue.UNKNOWN else of(annotationOwner as PsiModifierListOwner)
  }

  private fun fromModifierListOwner(modifierListOwner: PsiModifierListOwner): TaintValue {
    val annotationContext = AnnotationContext.fromModifierListOwner(modifierListOwner)
    return of(annotationContext)
  }

  fun of(context: AnnotationContext): TaintValue {
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
    if (info.kind != RestrictionInfo.RestrictionInfoKind.KNOWN) {
      info = context.secondaryItems().asSequence()
               .map { fromAnnotationOwner(it.modifierList) }
               .filter { it !== TaintValue.UNKNOWN }
               .firstOrNull() ?: info
    }
    if (info === TaintValue.UNKNOWN) {
      val obj: Any = if (owner is PsiParameter) owner.declarationScope else owner
      val member = (obj as? PsiMember)
      if (member != null) {
        info = of(member)
      }
    }
    return info
  }

  private fun fromExternalAnnotations(owner: PsiModifierListOwner): TaintValue {
    val annotationsManager = ExternalAnnotationsManager.getInstance(owner.project)
    val annotations = annotationsManager.findExternalAnnotations(owner) ?: return TaintValue.UNKNOWN
    return annotations.asSequence()
             .map { fromAnnotation(it) }
             .filterNotNull()
             .firstOrNull() ?: TaintValue.UNKNOWN
  }

  private fun of(annotationOwner: PsiModifierListOwner): TaintValue {
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
    return if (myUnTaintedAnnotations.contains(annotationQualifiedName)) {
      TaintValue.UNTAINTED
    }
    else null
  }

  private fun processJsr(qualifiedName: String?, annotation: PsiAnnotation): TaintValue? {
    if (qualifiedName == null ||
        qualifiedName != JAVAX_ANNOTATION_UNTAINTED ||
        !myUnTaintedAnnotations.contains(JAVAX_ANNOTATION_UNTAINTED)) {
      return null
    }
    val whenAttribute = annotation.findAttributeValue("when") ?: return null
    return if (whenAttribute.textMatches("ALWAYS")) TaintValue.UNTAINTED else null
  }

  fun fromAnnotation(target: PsiElement?): TaintValue? {
    if (target == null) return null
    val type = PsiUtil.getTypeByPsiElement(target) ?: return null
    if (target is PsiClass) return null
    var taintValue = tryFromCustom(target)
    if (taintValue != null) return taintValue
    if (target is PsiModifierListOwner) {
      taintValue = fromModifierListOwner(target)
      if (taintValue === TaintValue.UNKNOWN) taintValue = of(target)
      if (taintValue !== TaintValue.UNKNOWN) return taintValue
    }
    return fromAnnotationOwner(type)
  }

  private fun tryFromCustom(target: PsiElement): TaintValue? {
    return customFactories.asSequence()
      .map { it.invoke(target) }
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

  companion object {

    private fun fromMethod(context: UntaintedConfiguration): (PsiElement) -> TaintValue? {
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

    private fun fromField(context: UntaintedConfiguration): (PsiElement) -> TaintValue? {
      val methodNames: List<String?> = context.methodNames
      val methodClass: List<String?> = context.methodClass
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
      return {
        if (it is PsiMethod && matcher.matches(it)) {
          TaintValue.UNTAINTED
        }
        else {
          null
        }
      }
    }
  }
}