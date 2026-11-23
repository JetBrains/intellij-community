// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.testFramework.PerformanceUnitTest
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap

/**
 * Reports which test classes can hold a performance test.
 *
 * The finder reads the class file and never loads the class.
 * One instance serves every worker of a concurrent scan. The cache of the parsed class files is thread safe.
 */
internal class PerformanceUnitTestFinder(private val classLoader: ClassLoader) {
  // A missing class file maps to no entry, because `computeIfAbsent` stores no null value.
  private val parsed = ConcurrentHashMap<String, ClassInfo?>()

  /**
   * Reports whether [PerformanceUnitTest] marks the class named [className] or one of its public methods.
   *
   * The annotation is not `@Inherited`, so a mark on a superclass does not count.
   * A marked public method of a superclass or an interface counts.
   * The check can keep a class that the test process skips, for example a class that overrides a marked method without the mark.
   * A class file that the class path does not hold counts as a performance test, so the scan never drops a module by mistake.
   */
  fun hasPerformanceTest(className: String): Boolean {
    val info = parse(className.replace('.', '/')) ?: return true
    if (info.isAbstract) {
      return false
    }
    return info.isMarked || hasMarkedMethod(info)
  }

  private fun hasMarkedMethod(info: ClassInfo): Boolean {
    if (info.hasMarkedPublicMethod) {
      return true
    }
    return info.supertypes.any { supertype ->
      // The class loader holds the test class path and not the run-time image, so it cannot read a JDK class file.
      if (JDK_PACKAGE_PREFIXES.any(supertype::startsWith)) {
        return@any false
      }
      val supertypeInfo = parse(supertype) ?: return@any true
      hasMarkedMethod(supertypeInfo)
    }
  }

  private fun parse(internalName: String): ClassInfo? {
    return parsed.computeIfAbsent(internalName) { readClassFile(it) }
  }

  private fun readClassFile(internalName: String): ClassInfo? {
    val reader = classLoader.getResourceAsStream("$internalName.class")?.use(::ClassReader) ?: return null

    var isAbstract = false
    var isMarked = false
    var hasMarkedPublicMethod = false
    val supertypes = ArrayList<String>()
    val visitor = object : ClassVisitor(Opcodes.ASM9) {
      override fun visit(version: Int, access: Int, name: String, signature: String?, superClass: String?, interfaces: Array<String>?) {
        isAbstract = (access and Opcodes.ACC_ABSTRACT) != 0
        superClass?.let(supertypes::add)
        interfaces?.let(supertypes::addAll)
      }

      override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        // The annotation has the runtime retention, so only a visible annotation can match.
        if (visible && descriptor == PERFORMANCE_UNIT_TEST_DESCRIPTOR) {
          isMarked = true
        }
        return null
      }

      override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?,
      ): MethodVisitor? {
        if ((access and Opcodes.ACC_PUBLIC) == 0) {
          return null
        }
        return object : MethodVisitor(Opcodes.ASM9) {
          override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            if (visible && descriptor == PERFORMANCE_UNIT_TEST_DESCRIPTOR) {
              hasMarkedPublicMethod = true
            }
            return null
          }
        }
      }
    }
    reader.accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    return ClassInfo(isAbstract = isAbstract, isMarked = isMarked, hasMarkedPublicMethod = hasMarkedPublicMethod, supertypes = supertypes)
  }

  private class ClassInfo(
    @JvmField val isAbstract: Boolean,
    @JvmField val isMarked: Boolean,
    @JvmField val hasMarkedPublicMethod: Boolean,
    @JvmField val supertypes: List<String>,
  )

  private companion object {
    private val JDK_PACKAGE_PREFIXES = listOf("java/", "javax/", "jdk/", "sun/")
    private val PERFORMANCE_UNIT_TEST_DESCRIPTOR = "L${PerformanceUnitTest::class.java.name.replace('.', '/')};"
  }
}
