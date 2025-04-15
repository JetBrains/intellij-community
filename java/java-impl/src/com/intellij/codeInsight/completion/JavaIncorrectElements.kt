// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.AnnotationTargetUtil
import com.intellij.codeInsight.ExceptionUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.java.syntax.parser.JavaKeywords
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.addIfNotNull


interface LookupPositionMatcher {
  fun match(position: PsiElement): Boolean
  fun createIncorrectElementMatcher(position: PsiElement): (LookupElement) -> Boolean
}

object JavaIncorrectElements {
  private val matcherKey = Key<(LookupElement) -> Boolean>("JavaIncorrectElements.matcher")
  private val positions = listOf(
    ExceptionPositionMatcher, TryWithResourcesPositionMatcher, AnnotationPositionMatcher, TypeParameterPositionMatcher,
    ImplementsDeclarationPositionMatcher, ExtendsDeclarationPositionMatcher
  )

  fun matchPosition(position: PsiElement): LookupPositionMatcher? {
    return positions.firstOrNull { it.match(position) }
  }

  fun putMatcher(elementMatcher: (LookupElement) -> Boolean, context: UserDataHolder) {
    context.putUserData(matcherKey, elementMatcher)
  }

  fun tryGetMatcher(context: UserDataHolder): ((LookupElement) -> Boolean)? {
    return context.getUserData(matcherKey)
  }
}

private object AnnotationPositionMatcher: LookupPositionMatcher {
  private fun tryGetAnnotation(position: PsiElement): PsiAnnotation? {
    val parent = position.parent as? PsiJavaCodeReferenceElement ?: return null
    return parent.parent as? PsiAnnotation
  }

  override fun match(position: PsiElement): Boolean {
    return tryGetAnnotation(position) != null
  }

  override fun createIncorrectElementMatcher(position: PsiElement): (LookupElement) -> Boolean {
    val annotation = tryGetAnnotation(position) ?: throw AssertionError("Annotation is null")
    val targets = AnnotationTargetUtil.getTargetsForLocation(annotation.owner)
    return l@ { element ->
      val psiClass = element.`object` as? PsiClass ?: return@l false
      return@l !psiClass.isAnnotationType || AnnotationTargetUtil.findAnnotationTarget(psiClass, *targets) == null
    }
  }
}

private object TypeParameterPositionMatcher: LookupPositionMatcher {
  private fun tryGetTypeElement(position: PsiElement): PsiTypeElement? {
    val parent = position.parent as? PsiJavaCodeReferenceElement ?: return null
    return parent.parent as? PsiTypeElement
  }
  
  override fun match(position: PsiElement): Boolean {
    return tryGetTypeElement(position) != null
  }

  override fun createIncorrectElementMatcher(position: PsiElement): (LookupElement) -> Boolean {
    val typeElement = tryGetTypeElement(position)!!
    val bounds = PreferByKindWeigher.getTypeBounds(typeElement)
    return l@ { element ->
      val obj = element.`object`
      if (obj is PsiKeyword) return@l true
      val psiClass = obj as? PsiClass ?: return@l false
      return@l bounds.all { !InheritanceUtil.isInheritorOrSelf(psiClass, it, true) }
    }
  }
}

private object TryWithResourcesPositionMatcher: LookupPositionMatcher {
  override fun match(position: PsiElement): Boolean {
    return PreferByKindWeigher.IN_RESOURCE.accepts(position)
  }

  override fun createIncorrectElementMatcher(position: PsiElement): (LookupElement) -> Boolean {
    return this::match
  }

  private fun match(lookupElement: LookupElement): Boolean {
    val obj = lookupElement.`object`
    if (obj is PsiKeyword && obj.text in JavaKeywordCompletion.PRIMITIVE_TYPES) {
      return true
    }
    val psiClass = obj as? PsiClass ?: return false
    return !InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE)
  }
}

private object ExceptionPositionMatcher: LookupPositionMatcher {
  private fun isCatchClausePosition(position: PsiElement): Boolean {
    return PreferByKindWeigher.IN_CATCH_TYPE.accepts(position) || PreferByKindWeigher.IN_MULTI_CATCH_TYPE.accepts(position)
  }

  override fun match(position: PsiElement): Boolean {
    return isCatchClausePosition(position) ||
           PreferByKindWeigher.INSIDE_METHOD_THROWS_CLAUSE.accepts(position) ||
           JavaDocCompletionContributor.THROWS_TAG_EXCEPTION.accepts(position) ||
           JavaSmartCompletionContributor.AFTER_THROW_NEW.accepts(position)
  }

  override fun createIncorrectElementMatcher(position: PsiElement): (LookupElement) -> Boolean {
    val thrownCheckedExceptions = mutableListOf<PsiClass>()
    val isCatchClause = isCatchClausePosition(position)
    if (isCatchClause) {
      val container = PsiTreeUtil.getParentOfType(position, PsiTryStatement::class.java, PsiMethod::class.java)
      if (container != null) {
        val block = if (container is PsiTryStatement) container.tryBlock else container
        if (block != null) {
          for (type in ExceptionUtil.getThrownCheckedExceptions(block)) {
            thrownCheckedExceptions.addIfNotNull(type.resolve())
          }
        }
      }
    }

    return l@ { element ->
      val psiClass = element.`object` as? PsiClass ?: return@l false

      if (!InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return@l true
      }

      val psiManager = psiClass.manager

      // if exception is checked
      if (isCatchClause) {
        val qualifiedName = psiClass.qualifiedName
        return@l !ExceptionUtil.isUncheckedException(psiClass)
                 && qualifiedName != CommonClassNames.JAVA_LANG_THROWABLE
                 && qualifiedName != CommonClassNames.JAVA_LANG_EXCEPTION
                 && !thrownCheckedExceptions.any { psiManager.areElementsEquivalent(it, psiClass) }
      }

      return@l false
    }
  }
}

private object ImplementsDeclarationPositionMatcher: LookupPositionMatcher {
  private val INSIDE_IMPLEMENTS_LIST: ElementPattern<PsiElement> = PsiJavaPatterns.psiElement().afterLeaf(
    JavaKeywords.IMPLEMENTS, ",").inside(PsiJavaPatterns.psiElement(JavaElementType.IMPLEMENTS_LIST))

  override fun match(position: PsiElement): Boolean {
    return INSIDE_IMPLEMENTS_LIST.accepts(position)
  }

  override fun createIncorrectElementMatcher(position: PsiElement): (LookupElement) -> Boolean {
    return this::matchElement
  }

  private fun matchElement(element: LookupElement): Boolean {
    val psiClass = element.`object` as? PsiClass ?: return false
    return !psiClass.isInterface
  }
}

private object ExtendsDeclarationPositionMatcher: LookupPositionMatcher {
  private val EXTENDS_LIST: ElementPattern<PsiElement> = PsiJavaPatterns.psiElement().afterLeaf(
    JavaKeywords.EXTENDS, ",").inside(PsiJavaPatterns.psiElement(JavaElementType.EXTENDS_LIST))

  override fun match(position: PsiElement): Boolean {
    return EXTENDS_LIST.accepts(position)
  }

  override fun createIncorrectElementMatcher(position: PsiElement): (LookupElement) -> Boolean {
    val psiClass = position.parent.parent.parent as? PsiClass ?: return { false }

    if (psiClass.isInterface) {
      return this::matchClass
    }

    return this::matchElement
  }

  private fun matchClass(element: LookupElement): Boolean {
    val psiClass = element.`object` as? PsiClass ?: return false
    return !psiClass.isInterface
  }

  private fun matchElement(element: LookupElement): Boolean {
    val psiClass = element.`object` as? PsiClass ?: return false
    return psiClass.isInterface || psiClass.hasModifierProperty(PsiModifier.FINAL) // ...
  }
}