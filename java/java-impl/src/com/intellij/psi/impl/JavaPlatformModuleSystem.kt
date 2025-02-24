// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.java.JavaBundle
import com.intellij.java.codeserver.core.JpmsModuleAccessInfo.JpmsModuleAccessMode
import com.intellij.java.codeserver.core.JpmsModuleInfo
import com.intellij.java.codeserver.core.JpmsModuleInfo.TargetModuleInfo
import com.intellij.psi.JavaModuleSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaModule

/**
 * Checks package accessibility according to JLS 7 "Packages and Modules".
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jls/se9/html/jls-7.html">JLS 7 "Packages and Modules"</a>
 * @see <a href="http://openjdk.org/jeps/261">JEP 261: Module System</a>
 */
internal class JavaPlatformModuleSystem : JavaModuleSystem {
  override fun getName(): String = JavaBundle.message("java.platform.module.system.name")

  override fun isAccessible(targetPackageName: String, targetFile: PsiFile?, place: PsiElement): Boolean {
    val useFile = place.containingFile?.originalFile ?: return true
    val infos = JpmsModuleInfo.findTargetModuleInfos(targetPackageName, targetFile, useFile) ?: return true
    return infos.isNotEmpty() && infos.any { info -> info.accessAt(useFile).checkAccess(useFile, JpmsModuleAccessMode.EXPORT) == null }
  }

  override fun isAccessible(targetModule: PsiJavaModule, place: PsiElement): Boolean {
    val useFile = place.containingFile?.originalFile ?: return true
    return TargetModuleInfo(targetModule, "").accessAt(useFile).checkModuleAccess(place) == null
  }
}