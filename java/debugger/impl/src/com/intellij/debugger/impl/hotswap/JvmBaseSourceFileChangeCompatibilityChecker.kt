// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.SLRUMap
import com.intellij.xdebugger.impl.hotswap.HotSwapChangesCompatibility
import com.intellij.xdebugger.impl.hotswap.SourceFileChange
import com.intellij.xdebugger.impl.hotswap.SourceFileChangeCompatibilityChecker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
abstract class JvmBaseSourceFileChangeCompatibilityChecker(
  private val project: Project,
  private val fileType: FileType,
) : SourceFileChangeCompatibilityChecker {
  private val cache = ContentBasedCache()

  final override suspend fun getCompatibility(change: SourceFileChange): HotSwapChangesCompatibility {
    if (change.file.fileType != fileType) return HotSwapChangesCompatibility.Irrelevant
    return readAction { classify(project, change.file, change.oldContent) }
  }

  private fun classify(project: Project, file: VirtualFile, oldContent: CharSequence): HotSwapChangesCompatibility {
    val currentFile = PsiManager.getInstance(project).findFile(file) ?: return HotSwapChangesCompatibility.Unknown
    return classify(currentFile) {
      cache.fetchClassShapes(file, oldContent)
    }
  }

  @TestOnly
  fun classify(currentFile: PsiFile, oldFile: PsiFile): HotSwapChangesCompatibility {
    return classify(currentFile) {
      computeClassShapesBuildResult(oldFile)
    }
  }

  private fun classify(currentFile: PsiFile, oldClassShapes: () -> ClassShapesBuildResult?): HotSwapChangesCompatibility {
    val currentClasses = when (val result = computeClassShapesBuildResult(currentFile)) {
      null -> return HotSwapChangesCompatibility.Unknown
      is ClassShapesBuildResult.Built -> result.classShapes
      ClassShapesBuildResult.HasErrors -> return incompatible(HotSwapIncompatibilityReasons.compilationProblems())
      ClassShapesBuildResult.Unknown -> return HotSwapChangesCompatibility.Unknown
    }
    val oldClasses = when (val result = oldClassShapes()) {
      null -> return HotSwapChangesCompatibility.Unknown
      is ClassShapesBuildResult.Built -> result.classShapes
      ClassShapesBuildResult.HasErrors, ClassShapesBuildResult.Unknown -> return HotSwapChangesCompatibility.Unknown
    }
    findHotSwapIncompatibleClassReason(oldClasses, currentClasses)?.let { return incompatible(it) }
    return HotSwapChangesCompatibility.Compatible
  }

  protected abstract fun buildClassShapes(file: PsiFile): Map<String, HotSwapClassShape>

  protected fun unknownClassShapes(reason: String): Nothing {
    LOG.debug("Cannot build class shapes: $reason")
    throw CannotBuildClassShapesException(reason)
  }

  private fun computeClassShapesBuildResult(file: PsiFile): ClassShapesBuildResult? {
    if (hasErrors(file)) return ClassShapesBuildResult.HasErrors
    return try {
      ClassShapesBuildResult.Built(buildClassShapes(file))
    }
    catch (_: CannotBuildClassShapesException) {
      ClassShapesBuildResult.Unknown
    }
    catch (_: IndexNotReadyException) {
      null
    }
  }

  private fun incompatible(reason: String): HotSwapChangesCompatibility = HotSwapChangesCompatibility.Incompatible(reason)

  private fun hasErrors(file: PsiFile): Boolean = PsiTreeUtil.hasErrorElements(file)

  private class CannotBuildClassShapesException(reason: String) : RuntimeException(reason)

  private sealed interface ClassShapesBuildResult {
    data class Built(val classShapes: Map<String, HotSwapClassShape>) : ClassShapesBuildResult
    data object HasErrors : ClassShapesBuildResult
    data object Unknown : ClassShapesBuildResult
  }

  /**
   * Caches calls of [computeClassShapesBuildResult] for requests with the same file contents.
   */
  private inner class ContentBasedCache {
    private val files = SLRUMap<Key, PsiFile>(10, 10)
    private val classShapes = SLRUMap<Key, ClassShapesBuildResult>(10, 10)

    // Can be called from multiple threads, but the calls are sequential.
    fun fetchClassShapes(file: VirtualFile, oldContent: CharSequence): ClassShapesBuildResult? {
      val key = Key(file.name, oldContent.toString())
      synchronized(classShapes) {
        classShapes.get(key)?.let { return it }
      }

      val psiFile = fetchPsiFile(key)
      val result = computeClassShapesBuildResult(psiFile) ?: return null
      synchronized(classShapes) {
        classShapes.get(key)?.let { return it }
        classShapes.put(key, result)
      }
      return result
    }

    private fun fetchPsiFile(key: Key): PsiFile {
      synchronized(files) {
        files.get(key)?.let { return it }
      }

      val file = PsiFileFactory.getInstance(project).createFileFromText(key.fileName, fileType, key.oldText)
      synchronized(files) {
        files.get(key)?.let { return it }
        files.put(key, file)
      }
      return file
    }
  }

  private data class Key(val fileName: String, val oldText: String)

  companion object {
    private val LOG = Logger.getInstance(JvmBaseSourceFileChangeCompatibilityChecker::class.java)
  }
}

private fun findHotSwapIncompatibleClassReason(
  oldClasses: Map<String, HotSwapClassShape>,
  currentClasses: Map<String, HotSwapClassShape>,
): String? {
  for ((name, oldClass) in oldClasses) {
    val currentClass = currentClasses[name] ?: continue
    findHotSwapIncompatibleClassReason(oldClass, currentClass)?.let { return it }
  }
  return null
}

private fun findHotSwapIncompatibleClassReason(oldClass: HotSwapClassShape, currentClass: HotSwapClassShape): String? {
  if (oldClass.kind != currentClass.kind || oldClass.supers != currentClass.supers) {
    return HotSwapIncompatibilityReasons.structureModified()
  }
  if (oldClass.innerClasses != currentClass.innerClasses) {
    return HotSwapIncompatibilityReasons.structureModified()
  }
  if (oldClass.modifiers != currentClass.modifiers) {
    return HotSwapIncompatibilityReasons.classModifiersChanged()
  }
  findHotSwapIncompatibleFieldReason(oldClass.fields, currentClass.fields)?.let { return it }
  findHotSwapIncompatibleMethodReason(oldClass.methods, currentClass.methods)?.let { return it }
  return null
}

private fun findHotSwapIncompatibleFieldReason(
  oldFields: Map<String, HotSwapFieldShape>,
  currentFields: Map<String, HotSwapFieldShape>,
): String? {
  for ((name, oldField) in oldFields) {
    val currentField = currentFields[name] ?: return HotSwapIncompatibilityReasons.signatureModified()
    if (oldField.type != currentField.type) {
      return HotSwapIncompatibilityReasons.signatureModified()
    }
    if (oldField.modifiers != currentField.modifiers) {
      return HotSwapIncompatibilityReasons.signatureModified()
    }
  }
  for (name in currentFields.keys) {
    if (name !in oldFields) {
      return HotSwapIncompatibilityReasons.signatureModified()
    }
  }
  return null
}

private fun findHotSwapIncompatibleMethodReason(
  oldMethods: Map<HotSwapMethodId, HotSwapMethodShape>,
  currentMethods: Map<HotSwapMethodId, HotSwapMethodShape>,
): String? {
  for ((id, oldMethod) in oldMethods) {
    val currentMethod = currentMethods[id]
    if (currentMethod == null) {
      if (currentMethods.keys.any { it.name == id.name && it.isConstructor == id.isConstructor }) {
        return HotSwapIncompatibilityReasons.signatureModified()
      }
      return HotSwapIncompatibilityReasons.methodRemoved()
    }
    if (oldMethod.returnType != currentMethod.returnType) {
      return HotSwapIncompatibilityReasons.signatureModified()
    }
    if (oldMethod.modifiers != currentMethod.modifiers) {
      return HotSwapIncompatibilityReasons.methodModifiersChanged()
    }
  }
  for (id in currentMethods.keys) {
    if (id !in oldMethods) {
      if (oldMethods.keys.any { it.name == id.name && it.isConstructor == id.isConstructor }) {
        return HotSwapIncompatibilityReasons.signatureModified()
      }
      return HotSwapIncompatibilityReasons.methodAdded()
    }
  }
  return null
}
