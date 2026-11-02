// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.scratch

import com.intellij.debugger.NoDataException
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import org.jetbrains.annotations.Unmodifiable

/**
 * @author Eugene Zhuravlev
 */
open class JavaScratchPositionManager(debugProcess: DebugProcessImpl, private val myScratchFile: VirtualFile) : PositionManagerImpl(debugProcess) {
  @Throws(NoDataException::class)
  override fun locationsOfLine(type: ReferenceType, position: SourcePosition): List<Location> {
    checkPosition(position)
    return super.locationsOfLine(type, position)
  }

  @Throws(NoDataException::class)
  override fun createPrepareRequests(requestor: ClassPrepareRequestor, position: SourcePosition): List<ClassPrepareRequest> {
    checkPosition(position)
    return super.createPrepareRequests(requestor, position)
  }

  @Throws(NoDataException::class)
  override fun getAllClasses(position: SourcePosition): @Unmodifiable List<ReferenceType> {
    checkPosition(position)
    return super.getAllClasses(position)
  }

  @Throws(NoDataException::class)
  private fun checkPosition(position: SourcePosition) {
    if (myScratchFile != position.file.virtualFile) {
      throw NoDataException.INSTANCE
    }
  }

  @Throws(NoDataException::class)
  override fun getSourcePosition(location: Location?): SourcePosition? {
    val position = super.getSourcePosition(location)
    if (position == null) {
      throw NoDataException.INSTANCE
    }
    return position
  }

  override fun getPsiFileByLocation(project: Project, location: Location): PsiFile? {
    if (!myScratchFile.isValid) return null
    val refType = location.declaringType() ?: return null
    val psiFile = PsiManager.getInstance(project).findFile(myScratchFile) as? PsiJavaFile ?: return null
    val classes = psiFile.classes
    if (classes.isEmpty()) return null

    val originalQName = refType.name()
    for (psiClass in classes) {
      if (originalQName == psiClass.qualifiedName) {
        return psiFile
      }
    }

    val dollar = originalQName.indexOf('$')
    val alternativeQName = if (dollar > 0) originalQName.substring(0, dollar) else null
    if (!alternativeQName.isNullOrEmpty()) {
      for (psiClass in classes) {
        if (alternativeQName == psiClass.qualifiedName) {
          return psiFile
        }
      }
    }
    return null
  }
}
