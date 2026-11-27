// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.javaModuleCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir
import com.intellij.util.io.java.classFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class ResolveInLibraries2Test {
  private val myFixture by javaModuleCodeInsightFixture(PathManager.getHomePath())
  private val project get() = myFixture.project
  private val module get() = myFixture.module

  @Test
  fun `test prefer current library when navigation from its source`(): Unit = timeoutRunBlocking {
    val libsDir = directoryContent {
      zip("foo.jar") {
        classFile("foo.Bar") {}
        classFile("foo.Foo") {
          field("bar", "foo.Bar")
        }
      }
      zip("foo-src.zip") {
        dir("foo") {
          file("Bar.java", "package foo;\npublic class Bar {}")
          file("Foo.java", """package foo;
                             |public class Foo {
                             |  private Bar bar;
                             |}""".trimIndent())
        }
      }
    }.generateInVirtualTempDir()
    val jarCopy = myFixture.copyFileToProject(libsDir.path + "/foo.jar", "lib/foo.jar")
    val srcPath = libsDir.path + "/foo-src.zip"
    val srcCopy = myFixture.copyFileToProject(srcPath, "lib/foo-src.zip")

    fun getJarRootUrls(path: String) = listOf(VfsUtil.getUrlForLibraryRoot(File(path)))

    ModuleRootModificationUtil.addModuleLibrary(module, "nano1", getJarRootUrls("${libsDir.path}/foo.jar"), getJarRootUrls("${libsDir.path}/foo-src.zip"))
    ModuleRootModificationUtil.addModuleLibrary(module, "nano2", getJarRootUrls(jarCopy.path), getJarRootUrls(srcCopy.path))
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    readAction {
      val parsers = JavaPsiFacade.getInstance(project).findClasses("foo.Foo", GlobalSearchScope.allScope(project))
      assertEquals(2, parsers.size)

      val file0 = parsers[0].navigationElement.containingFile
      assertTrue(file0.virtualFile.path.startsWith(libsDir.path))
      assertTrue(file0.findReferenceAt(file0.text.indexOf("Bar bar"))!!.resolve()!!.navigationElement.containingFile.virtualFile.path.startsWith(libsDir.path))

      val file1 = parsers[1].navigationElement.containingFile
      assertTrue(file1.virtualFile.path.startsWith (srcCopy.path))
      assertTrue(file1.findReferenceAt(file1.text.indexOf("Bar bar"))!!.resolve()!!.navigationElement.containingFile.virtualFile.path.startsWith(srcCopy.path))
    }
  }

  @Test
  fun `test choose source from libraries in stable order`(): Unit = timeoutRunBlocking {
    val libsDir = directoryContent {
      zip("foo.jar") {
        classFile("foo.Foo") {}
      }
      zip("z-src.zip") {
        dir("foo") {
          file("Foo.java", "package foo; public class Foo { int fromZ; }")
        }
      }
      zip("a-src.zip") {
        dir("foo") {
          file("Foo.java", "package foo; public class Foo { int fromA; }")
        }
      }
    }.generateInVirtualTempDir()

    fun getJarRootUrls(path: String) = listOf(VfsUtil.getUrlForLibraryRoot(File(path)))

    val classes = getJarRootUrls("${libsDir.path}/foo.jar")
    ModuleRootModificationUtil.addModuleLibrary(module, "z-library", classes, getJarRootUrls("${libsDir.path}/z-src.zip"))
    ModuleRootModificationUtil.addModuleLibrary(module, "a-library", classes, getJarRootUrls("${libsDir.path}/a-src.zip"))
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    readAction {
      val cls = requireNotNull(JavaPsiFacade.getInstance(project).findClass("foo.Foo", GlobalSearchScope.allScope(project)))

      val source = cls.navigationElement.containingFile
      assertTrue(source.virtualFile.path.startsWith("${libsDir.path}/a-src.zip"))
      assertTrue(source.text.contains("fromA"))
    }
  }
}
