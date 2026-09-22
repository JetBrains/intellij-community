// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.idea.IJIgnore
import com.intellij.testFramework.SkipInHeadlessEnvironment
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap

/**
 * Reports which test classes the test runner must run outside a headless environment.
 *
 * The finder reads the class file and never loads the class.
 * A load runs the initializer of the test class, and the JVM also runs the initializer of every enum that an
 * annotation of the class uses as a default value. Such an initializer can be slow, and it can deadlock the build.
 *
 * One instance serves every worker of a concurrent scan. The cache of the parsed class files is thread safe.
 */
internal class HeadlessSkippedTestFinder(private val classLoader: ClassLoader) {
  private val parsed = ConcurrentHashMap<String, ClassInfo>()

  /**
   * Reports whether [SkipInHeadlessEnvironment] marks the class named [className], and [IJIgnore] does not.
   *
   * [className] is a fully qualified name. A nested class keeps the `$` separator, as in `com.example.OuterTest$Inner`.
   */
  fun isSkippedInHeadlessEnvironment(className: String): Boolean {
    val info = parse(className.replace('.', '/'))
    if (info.isAbstract || info.annotations.contains(IJ_IGNORE_DESCRIPTOR)) {
      return false
    }
    return hasSkipAnnotation(info)
  }

  /**
   * [SkipInHeadlessEnvironment] is `@Inherited`, so the JVM reports it on a subclass of a marked class too.
   * The walk covers the superclasses only, because `@Inherited` ignores an interface.
   */
  private fun hasSkipAnnotation(info: ClassInfo): Boolean {
    var current = info
    while (true) {
      if (current.annotations.contains(SKIP_IN_HEADLESS_ENVIRONMENT_DESCRIPTOR)) {
        return true
      }
      val superName = current.superName ?: return false
      // The class loader holds the test class path and not the run-time image, so it cannot read a JDK class file.
      // A JDK class carries no annotation of this repository, so the walk stops there.
      if (JDK_PACKAGE_PREFIXES.any(superName::startsWith)) {
        return false
      }
      current = parse(superName)
    }
  }

  private fun parse(internalName: String): ClassInfo {
    return parsed.computeIfAbsent(internalName) { readClassFile(it) }
  }

  private fun readClassFile(internalName: String): ClassInfo {
    val resourceName = "$internalName.class"
    // The class loader reads the same class path that a load of the class reads, so it finds the same class file.
    val reader = checkNotNull(classLoader.getResourceAsStream(resourceName)) {
      "Cannot find '$resourceName' on the test class path"
    }.use(::ClassReader)

    var isAbstract = false
    var superName: String? = null
    val annotations = HashSet<String>()
    val visitor = object : ClassVisitor(Opcodes.ASM9) {
      override fun visit(version: Int, access: Int, name: String, signature: String?, superClass: String?, interfaces: Array<String>?) {
        isAbstract = (access and Opcodes.ACC_ABSTRACT) != 0
        superName = superClass
      }

      override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
        // Both annotations have the runtime retention, so only a visible annotation can match.
        if (visible) {
          annotations.add(descriptor)
        }
        return null
      }
    }
    reader.accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    return ClassInfo(isAbstract = isAbstract, superName = superName, annotations = annotations)
  }

  private class ClassInfo(
    @JvmField val isAbstract: Boolean,
    @JvmField val superName: String?,
    @JvmField val annotations: Set<String>,
  )

  private companion object {
    private val JDK_PACKAGE_PREFIXES = listOf("java/", "javax/", "jdk/", "sun/")
    private val SKIP_IN_HEADLESS_ENVIRONMENT_DESCRIPTOR = descriptorOf(SkipInHeadlessEnvironment::class.java)
    private val IJ_IGNORE_DESCRIPTOR = descriptorOf(IJIgnore::class.java)

    private fun descriptorOf(annotation: Class<out Annotation>): String = "L${annotation.name.replace('.', '/')};"
  }
}
