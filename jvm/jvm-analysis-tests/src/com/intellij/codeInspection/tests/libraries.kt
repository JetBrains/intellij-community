package com.intellij.codeInspection.tests

import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus
import java.io.File

internal fun ModifiableRootModel.addJavaAnnotationsLibrary() {
  val annotationsJar = File(PathUtil.getJarPathForClass(ApiStatus::class.java))
  PsiTestUtil.addLibrary(this, "java-annotations", annotationsJar.parent, annotationsJar.name)
}