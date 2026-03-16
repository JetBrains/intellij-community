// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.bytecode

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.signature.SignatureReader
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor
import java.nio.file.Path
import java.util.Collections
import kotlin.io.path.exists
import kotlin.io.path.readBytes

/**
 * Provides a way to recursively find all superclasses and classes mentioned in their generic signatures for a given class
 */
internal class ClassAncestorResolver(private val classpath: List<Path>) {
  private val packageToClasspathRoots by lazy {
    collectPackagesInClasspath(classpath)
  }
  private val ancestorsCache = HashMap<String, List<String>>()

  fun getAllAncestors(binaryClassName: String): List<String> {
    val cached = ancestorsCache[binaryClassName]
    if (cached != null) return cached
    ancestorsCache[binaryClassName] = emptyList() //this is needed to avoid StackOverflowError if the class refers to itself from generic parameters
    val ancestors = collectAllAncestors(binaryClassName)
    ancestorsCache[binaryClassName] = ancestors
    return ancestors
  }

  private fun collectAllAncestors(binaryClassName: String): List<String> {
    LOG.trace { "Collecting ancestors for $binaryClassName" }
    val superclasses = collectSuperclasses(binaryClassName)
    val result = HashSet<String>()
    for (superclass in superclasses) {
      result.add(superclass)
      result.addAll(getAllAncestors(superclass))
    }
    return result.toList()
  }

  private fun collectPackagesInClasspath(classpath: List<Path>): Map<String, List<Path>> {
    data class PackageToClasspathRoot(val packageName: String, val classpathRoot: Path)

    return classpath
      .asSequence()
      .flatMap { classpathRoot ->
        withClassRootEntries(classpathRoot) {
          it.map { entry ->
            val packageName = entry.entryName.substringBeforeLast(delimiter = '/', missingDelimiterValue = "")
            PackageToClasspathRoot(packageName, classpathRoot)
          }.toSet()
        }
      }
      .groupBy({ it.packageName }, { it.classpathRoot })
  }

  private fun collectSuperclasses(binaryClassName: String): List<String> {
    val packageName = binaryClassName.substringBeforeLast(delimiter = '/', missingDelimiterValue = "")
    val relativePath = "$binaryClassName.class"
    val paths = packageToClasspathRoots[packageName] ?: return emptyList()
    return paths
      .firstNotNullOfOrNull { root ->
        withClassRoot(root) { classRoot ->
          val classFilePath = classRoot.resolve(relativePath)
          if (classFilePath.exists()) loadSuperclasses(classFilePath)
          else null
        }
      } ?: emptyList()
  }

  private fun loadSuperclasses(path: Path): List<String> {
    val result = ArrayList<String>()
    val visitor = object : ClassVisitor(Opcodes.API_VERSION, ) {
      override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String?>?) {
        if (superName != null) {
          result.add(superName)
        }
        if (interfaces != null) {
          Collections.addAll(result, *interfaces)
        }
        if (signature != null) {
          SignatureReader(signature).accept(object : SignatureVisitor(Opcodes.API_VERSION) {
            override fun visitClassType(name: String) {
              result.add(name)
            }
          })
        }
      }
    }
    ClassReader(path.readBytes()).accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    return result
  }
}

private val LOG = logger<ClassAncestorResolver>()