// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.extractModule

import com.intellij.java.analysis.bytecode.JvmBytecodeAnalysis
import com.intellij.java.analysis.bytecode.JvmBytecodeReferenceProcessor
import com.intellij.java.analysis.bytecode.JvmClassBytecodeDeclaration
import java.nio.file.Path

internal class ExtractModuleFileProcessor {
  private val mutableReferencedClasses: MutableSet<String> = HashSet()
  private val mutableGatheredClassLinks: MutableMap<String, MutableSet<String>> = HashMap()

  private val referenceProcessor = object : JvmBytecodeReferenceProcessor {
    override fun processClassReference(targetClass: JvmClassBytecodeDeclaration, sourceClass: JvmClassBytecodeDeclaration) {
      val targetClassName = targetClass.topLevelSourceClassName
      if (targetClassName.startsWith("[L")) return  // ignore array classes
      val sourceClassName = sourceClass.topLevelSourceClassName
      if (sourceClassName != targetClassName) {
        mutableReferencedClasses.add(targetClassName)
        mutableGatheredClassLinks.computeIfAbsent(sourceClassName) { HashSet() }.add(targetClassName)
      }
    }
  }

  val classFileAnalyzer = JvmBytecodeAnalysis.getInstance().createReferenceAnalyzer(referenceProcessor)

  val referencedClasses: Set<String>
    get() = mutableReferencedClasses

  val gatheredClassLinks: Map<String, Set<String>>
    get() = mutableGatheredClassLinks

  fun processFile(path: Path) = classFileAnalyzer.processFile(path)
}