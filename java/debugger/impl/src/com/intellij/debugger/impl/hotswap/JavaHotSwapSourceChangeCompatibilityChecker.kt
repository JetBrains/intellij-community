// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.impl.DebuggerSession
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.impl.hotswap.SourceFileChangeCompatibilityChecker
import org.jetbrains.annotations.ApiStatus

internal class JavaHotSwapSourceChangeCompatibilityCheckerProvider : HotSwapSourceChangeCompatibilityCheckerProvider {
  override fun provideCheckersForSession(debuggerSession: DebuggerSession): List<SourceFileChangeCompatibilityChecker> {
    return listOf(JavaHotSwapSourceChangeCompatibilityChecker(debuggerSession.project))
  }
}

@ApiStatus.Internal
class JavaHotSwapSourceChangeCompatibilityChecker(project: Project) :
  JvmBaseSourceFileChangeCompatibilityChecker(project, JavaFileType.INSTANCE) {

  override fun buildClassShapes(file: PsiFile): Map<String, HotSwapClassShape> {
    val javaFile = file as? PsiJavaFile ?: unknownClassShapes("Expected PsiJavaFile, got ${file::class.java.name}")
    return javaFile.classes.flatMap { it.snapshot() }.associateBy { it.name }
  }

  private fun PsiClass.snapshot(prefix: String = "", syntheticName: String? = null): List<HotSwapClassShape> {
    val declaredName = syntheticName ?: this.className()
    val className = prefix + declaredName
    val innerClasses = sourceInnerClasses()
    val shape = HotSwapClassShape(
      className,
      kind(),
      modifiers(CLASS_MODIFIERS),
      supers(),
      innerClasses.mapTo(hashSetOf()) { "$className.${it.name}" },
      sourceFields().associate { it.snapshot() } + capturedFields(),
      sourceMethods().associate { it.snapshot() } + sourceLambdas().mapIndexed { index, lambda -> lambda.snapshot(index) },
    )
    return listOf(shape) + innerClasses.flatMap { it.psiClass.snapshot("$className.", it.name) }
  }

  private fun PsiClass.sourceFields(): List<PsiField> = children.filterIsInstance<PsiField>()

  private fun PsiClass.sourceMethods(): List<PsiMethod> = children.filterIsInstance<PsiMethod>()

  private fun PsiClass.sourceInnerClasses(): List<SourceInnerClass> {
    val namedClasses = children.filterIsInstance<PsiClass>()
      .filterNot { it is PsiAnonymousClass }
      .map { SourceInnerClass(it, it.className()) }
    val anonymousClasses = PsiTreeUtil.collectElementsOfType(this, PsiAnonymousClass::class.java)
      .filter { it.enclosingSourceClass() == this }
      .mapIndexed { index, anonymousClass ->
        SourceInnerClass(anonymousClass, "anonymous$index")
      }
    return namedClasses + anonymousClasses
  }

  private fun PsiClass.className(): @NlsSafe String =
    this.qualifiedName ?: this.name ?: unknownClassShapes("Cannot determine Java class name in ${this.containingFile.name}")

  private fun PsiClass.sourceLambdas(): List<PsiLambdaExpression> =
    PsiTreeUtil.collectElementsOfType(this, PsiLambdaExpression::class.java)
      .filter { it.enclosingSourceClass() == this }

  private fun PsiElement.enclosingSourceClass(): PsiClass? = PsiTreeUtil.getParentOfType(this, PsiClass::class.java, true)

  private fun PsiClass.kind(): String = when {
    isAnnotationType -> "annotation"
    isEnum -> "enum"
    isInterface -> "interface"
    isRecord -> "record"
    else -> "class"
  }

  private fun PsiClass.supers(): Set<String> =
    (extendsListTypes.asSequence() + implementsListTypes.asSequence())
      .map { it.signature() }
      .toSet()

  private fun PsiField.snapshot(): Pair<String, HotSwapFieldShape> =
    name to HotSwapFieldShape(type.signature(), modifiers(FIELD_MODIFIERS))

  private fun PsiMethod.snapshot(): Pair<HotSwapMethodId, HotSwapMethodShape> {
    val id = HotSwapMethodId(name, isConstructor, parameterList.parameters.map { it.type.signature() })
    val returnType = returnType?.signature()
    return id to HotSwapMethodShape(returnType, modifiers(METHOD_MODIFIERS))
  }

  private fun PsiLambdaExpression.snapshot(index: Int): Pair<HotSwapMethodId, HotSwapMethodShape> {
    val capturedParameters = capturedVariables().map { it.type.signature() }
    val declaredParameters = parameterList.parameters.map { it.type.signature() }
    val id = HotSwapMethodId("lambda$" + syntheticOwnerName() + "$" + index, false, capturedParameters + declaredParameters)
    val returnType = functionalInterfaceType?.let { LambdaUtil.getFunctionalInterfaceReturnType(it)?.signature() }
    return id to HotSwapMethodShape(returnType, emptySet())
  }

  private fun PsiLambdaExpression.syntheticOwnerName(): String {
    val method = PsiTreeUtil.getParentOfType(this, PsiMethod::class.java, true)
    return when {
      method == null -> "initializer"
      method.isConstructor -> "new"
      else -> method.name
    }
  }

  private fun PsiClass.capturedFields(): Map<String, HotSwapFieldShape> {
    if (this !is PsiAnonymousClass) return emptyMap()
    return capturedVariables().mapIndexed { index, variable ->
      "capture$index${variable.name}" to HotSwapFieldShape(variable.type.signature(), emptySet())
    }.toMap()
  }

  private fun PsiElement.capturedVariables(): List<PsiVariable> {
    val variables = linkedSetOf<PsiVariable>()
    accept(object : JavaRecursiveElementWalkingVisitor() {
      override fun visitReferenceExpression(expression: PsiReferenceExpression) {
        super.visitReferenceExpression(expression)
        val variable = expression.resolve() as? PsiVariable ?: return
        if (variable is PsiField || PsiTreeUtil.isAncestor(this@capturedVariables, variable, false)) return
        variables.add(variable)
      }
    })
    return variables.sortedBy { it.textOffset }
  }

  private fun PsiType.signature(): String = canonicalText

  private fun PsiModifierListOwner.modifiers(knownModifiers: Array<String>): Set<String> =
    knownModifiers.filterTo(hashSetOf()) { modifierList?.hasExplicitModifier(it) == true }
}

private data class SourceInnerClass(val psiClass: PsiClass, val name: String)

private val CLASS_MODIFIERS = arrayOf(
  PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.STATIC, PsiModifier.FINAL, PsiModifier.ABSTRACT,
)
private val FIELD_MODIFIERS = arrayOf(
  PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.STATIC, PsiModifier.FINAL, PsiModifier.VOLATILE,
  PsiModifier.TRANSIENT,
)
private val METHOD_MODIFIERS = arrayOf(
  PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.STATIC, PsiModifier.FINAL, PsiModifier.ABSTRACT,
  PsiModifier.NATIVE, PsiModifier.SYNCHRONIZED, PsiModifier.STRICTFP,
)
