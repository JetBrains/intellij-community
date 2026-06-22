// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.impl.DebuggerSession
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiType
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

  private fun PsiClass.snapshot(prefix: String = ""): List<HotSwapClassShape> {
    val declaredName = qualifiedName ?: name ?: unknownClassShapes("Cannot determine Java class name in ${containingFile.name}")
    val className = prefix + declaredName
    val innerClasses = sourceInnerClasses()
    val shape = HotSwapClassShape(
      className,
      kind(),
      modifiers(CLASS_MODIFIERS),
      supers(),
      innerClasses.mapTo(hashSetOf()) { it.snapshotName("$className.") },
      sourceFields().associate { it.snapshot() },
      sourceMethods().associate { it.snapshot() },
    )
    return listOf(shape) + innerClasses.flatMap { it.snapshot("$className.") }
  }

  private fun PsiClass.snapshotName(prefix: String = ""): String = prefix + (qualifiedName ?: name.orEmpty())

  private fun PsiClass.sourceFields(): List<PsiField> = children.filterIsInstance<PsiField>()

  private fun PsiClass.sourceMethods(): List<PsiMethod> = children.filterIsInstance<PsiMethod>()

  private fun PsiClass.sourceInnerClasses(): List<PsiClass> = children.filterIsInstance<PsiClass>()

  private fun PsiClass.kind(): String = when {
    isAnnotationType -> "annotation"
    isEnum -> "enum"
    isInterface -> "interface"
    isRecord -> "record"
    else -> "class"
  }

  private fun PsiClass.supers(): Set<String> =
    (extendsList?.referenceElements.orEmpty().asSequence() + implementsList?.referenceElements.orEmpty().asSequence())
      .map { it.text.typeSignature() }
      .toSet()

  private fun PsiField.snapshot(): Pair<String, HotSwapFieldShape> =
    name to HotSwapFieldShape(typeElement?.text?.typeSignature() ?: type.signature(), modifiers(FIELD_MODIFIERS))

  private fun PsiMethod.snapshot(): Pair<HotSwapMethodId, HotSwapMethodShape> {
    val id = HotSwapMethodId(name, isConstructor, parameterList.parameters.map { it.typeElement?.text?.typeSignature() ?: it.type.signature() })
    val returnType = returnTypeElement?.text?.typeSignature() ?: returnType?.signature()
    return id to HotSwapMethodShape(returnType, modifiers(METHOD_MODIFIERS))
  }

  private fun PsiType.signature(): String = canonicalText

  private fun String.typeSignature(): String = filterNot { it.isWhitespace() }

  private fun PsiModifierListOwner.modifiers(knownModifiers: Array<String>): Set<String> =
    knownModifiers.filterTo(hashSetOf()) { modifierList?.hasExplicitModifier(it) == true }
}

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
