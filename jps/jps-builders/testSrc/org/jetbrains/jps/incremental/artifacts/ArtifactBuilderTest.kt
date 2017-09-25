/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental.artifacts

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.directoryContent
import org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.archive
import org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.root
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.util.JpsPathUtil
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.jar.JarFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * @author nik
 */
class ArtifactBuilderTest : ArtifactBuilderTestCase() {
  fun testFileCopy() {
    val a = addArtifact(root().fileCopy(createFile("file.txt", "foo")))
    buildAll()
    assertOutput(a, directoryContent { file("file.txt", "foo") })
  }

  fun testDir() {
    val a = addArtifact(
      root()
        .fileCopy(createFile("abc.txt"))
          .dir("dir")
          .fileCopy(createFile("xxx.txt", "bar"))
    )
    buildAll()
    assertOutput(a, directoryContent {
      file("abc.txt")
      dir("dir") {
        file("xxx.txt", "bar")
      }
    })
  }

  fun testArchive() {
    val a = addArtifact(
      root()
        .archive("xxx.zip")
          .fileCopy(createFile("X.class", "data"))
          .dir("dir")
            .fileCopy(createFile("Y.class"))
    )
    buildAll()
    assertOutput(a, directoryContent {
      zip("xxx.zip") {
        file("X.class", "data")
        dir("dir") {
          file("Y.class")
        }
      }
    })
  }

  fun testTwoDirsInArchive() {
    val dir1 = PathUtil.getParentPath(PathUtil.getParentPath(createFile("dir1/a/x.txt")))
    val dir2 = PathUtil.getParentPath(PathUtil.getParentPath(createFile("dir2/a/y.txt")))
    val a = addArtifact(
      root()
        .archive("a.jar")
          .dirCopy(dir1)
          .dirCopy(dir2)
          .dir("a").fileCopy(createFile("z.txt"))
    )
    buildAll()
    assertOutput(a, directoryContent {
      zip("a.jar") {
        dir("a") {
          file("x.txt")
          file("y.txt")
          file("z.txt")
        }
      }
    })
  }

  fun testArchiveInArchive() {
    val a = addArtifact(
      root()
        .archive("a.jar")
        .archive("b.jar")
        .fileCopy(createFile("xxx.txt", "foo"))
    )
    buildAll()
    assertOutput(a, directoryContent {
      zip("a.jar") {
        zip("b.jar") {
          file("xxx.txt", "foo")
        }
      }
    })
  }

  fun testIncludedArtifact() {
    val included = addArtifact("included",
                               root()
                                 .fileCopy(createFile("aaa.txt")))
    val a = addArtifact(
      root()
        .dir("dir")
        .artifact(included)
        .end()
        .fileCopy(createFile("bbb.txt"))
    )
    buildAll()

    assertOutput(included, directoryContent { file("aaa.txt") })
    assertOutput(a, directoryContent {
      dir("dir") {
        file("aaa.txt")
      }
      file("bbb.txt")
  })
  }

  fun testMergeDirectories() {
    val included = addArtifact("included",
                               root().dir("dir").fileCopy(createFile("aaa.class")))
    val a = addArtifact(
      root()
        .artifact(included)
        .dir("dir")
        .fileCopy(createFile("bbb.class")))
    buildAll()
    assertOutput(a, directoryContent {
      dir("dir") {
        file("aaa.class")
        file("bbb.class")
      }
    })
  }

  fun testCopyLibrary() {
    val library = addProjectLibrary("lib", createFile("lib/a.jar"))
    val a = addArtifact(root().lib(library))
    buildAll()
    assertOutput(a, directoryContent { file("a.jar") })
  }

  fun testModuleOutput() {
    val file = createFile("src/A.java", "public class A {}")
    val module = addModule("a", PathUtil.getParentPath(file))
    val artifact = addArtifact(root().module(module))

    buildArtifacts(artifact)
    assertOutput(artifact, directoryContent { file("A.class") })
  }

  fun testCopyResourcesFromModuleOutput() {
    val file = createFile("src/a.xml", "")
    JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(myProject).addResourcePattern("*.xml")
    val module = addModule("a", PathUtil.getParentPath(file))
    val artifact = addArtifact(root().module(module))
    buildArtifacts(artifact)
    assertOutput(artifact, directoryContent { file("a.xml") })
  }

  fun testIgnoredFile() {
    val file = createFile("a/.svn/a.txt")
    createFile("a/svn/b.txt")
    val a = addArtifact(root().parentDirCopy(PathUtil.getParentPath(file)))
    buildAll()
    assertOutput(a, directoryContent { dir("svn") { file("b.txt") }})
  }

  fun testIgnoredFileInArchive() {
    val file = createFile("a/.svn/a.txt")
    createFile("a/svn/b.txt")
    val a = addArtifact(archive("a.jar").parentDirCopy(PathUtil.getParentPath(file)))
    buildAll()
    assertOutput(a, directoryContent { zip("a.jar") { dir("svn") { file("b.txt")}}})
  }

  fun testCopyExcludedFolder() {
    //explicitly added excluded files should be copied (e.g. compile output)
    val file = createFile("xxx/excluded/a.txt")
    createFile("xxx/excluded/CVS")
    val excluded = PathUtil.getParentPath(file)
    val dir = PathUtil.getParentPath(excluded)

    val module = addModule("myModule")
    module.contentRootsList.addUrl(JpsPathUtil.pathToUrl(dir))
    module.excludeRootsList.addUrl(JpsPathUtil.pathToUrl(excluded))

    val a = addArtifact(root().dirCopy(excluded))
    buildAll()
    assertOutput(a, directoryContent { file("a.txt") })
  }

  fun testCopyExcludedFile() {
    //excluded files under non-excluded directory should not be copied
    val file = createFile("xxx/excluded/a.txt")
    createFile("xxx/b.txt")
    createFile("xxx/CVS")
    val dir = PathUtil.getParentPath(PathUtil.getParentPath(file))

    val module = addModule("myModule")
    module.contentRootsList.addUrl(JpsPathUtil.pathToUrl(dir))
    module.excludeRootsList.addUrl(JpsPathUtil.pathToUrl(PathUtil.getParentPath(file)))

    val a = addArtifact(root().dirCopy(dir))
    buildAll()
    assertOutput(a, directoryContent { file("b.txt") })
  }

  fun testExtractDirectory() {
    val jarFile = createXJarFile()
    val a = addArtifact("a", root().dir("dir").extractedDir(jarFile, "/dir"))
    buildAll()
    assertOutput(a, directoryContent {
      dir("dir") {
        file("file.txt", "text")
      }
    })
  }

  @Throws(IOException::class)
  fun testExtractDirectoryFromExcludedJar() {
    val jarPath = createFile("dir/lib/j.jar")
    val libDir = File(getOrCreateProjectDir(), "dir/lib")
    directoryContent {
      zip("j.jar") {
        file("a.txt", "a")
      }
    }.generate(libDir)
    val module = addModule("m")
    val libDirPath = FileUtil.toSystemIndependentName(libDir.absolutePath)
    module.contentRootsList.addUrl(JpsPathUtil.pathToUrl(PathUtil.getParentPath(libDirPath)))
    module.excludeRootsList.addUrl(JpsPathUtil.pathToUrl(libDirPath))
    val a = addArtifact("a", root().extractedDir(jarPath, ""))
    buildAll()
    assertOutput(a, directoryContent {
      file("a.txt", "a")
    })
  }

  fun testPackExtractedDirectory() {
    val zipPath = createXJarFile()
    val a = addArtifact("a", root().archive("a.jar").extractedDir(zipPath, "/dir"))
    buildAll()
    assertOutput(a, directoryContent { zip("a.jar") {
      file("file.txt", "text")
    }})
  }

  fun `test no duplicated directory entries for extracted directory packed into JAR file`() {
    val zipPath = createXJarFile()
    val a = addArtifact("a", root().archive("a.jar").extractedDir(zipPath, ""))
    buildAll()
    ZipFile(File(a.outputPath, "a.jar")).use {
      assertNotNull(it.getEntry("dir/"))
      assertNull(it.getEntry("dir//"))
    }
  }

  private fun createXJarFile(): String {
    val zipDir = directoryContent {
      zip("x.jar") {
        dir("dir") {
          file("file.txt", "text")
        }
      }
    }.generateInTempDir()
    return FileUtil.toSystemIndependentName(File(zipDir, "x.jar").absolutePath)
  }

  fun testSelfIncludingArtifact() {
    val a = addArtifact("a", root())
    LayoutElementTestUtil.addArtifactToLayout(a, a)
    assertBuildFailed(a)
  }

  fun testCircularInclusion() {
    val a = addArtifact("a", root())
    val b = addArtifact("b", root())
    LayoutElementTestUtil.addArtifactToLayout(a, b)
    LayoutElementTestUtil.addArtifactToLayout(b, a)
    assertBuildFailed(a)
    assertBuildFailed(b)
  }

  fun testArtifactContainingSelfIncludingArtifact() {
    val c = addArtifact("c", root())
    val a = addArtifact("a", root().artifact(c))
    LayoutElementTestUtil.addArtifactToLayout(a, a)
    val b = addArtifact("b", root().artifact(a))

    buildArtifacts(c)
    assertBuildFailed(b)
    assertBuildFailed(a)
  }

  fun testArtifactContainingSelfIncludingArtifactWithoutOutput() {
    val a = addArtifact("a", root())
    LayoutElementTestUtil.addArtifactToLayout(a, a)
    val b = addArtifact("b", root().artifact(a))
    a.outputPath = null

    assertBuildFailed(b)
  }

  //IDEA-73893
  @Throws(IOException::class)
  fun testManifestFileIsFirstEntry() {
    val firstFile = createFile("src/A.txt")
    val manifestFile = createFile("src/MANIFEST.MF")
    val lastFile = createFile("src/Z.txt")
    val a = addArtifact(archive("a.jar").dir("META-INF")
                          .fileCopy(firstFile).fileCopy(manifestFile).fileCopy(lastFile))
    buildArtifacts(a)
    val jarPath = a.outputPath!! + "/a.jar"
    val jarFile = JarFile(File(jarPath))
    jarFile.use {
      val entries = it.entries()
      assertTrue(entries.hasMoreElements())
      val firstEntry = entries.nextElement()
      assertEquals(JarFile.MANIFEST_NAME, firstEntry.name)
    }
  }

  @Throws(IOException::class)
  fun testPreserveCompressionMethodForEntryExtractedFromOneArchiveAndPackedIntoAnother() {
    val path = createFile("data/a.jar")
    val output = ZipOutputStream(BufferedOutputStream(FileOutputStream(File(path))))
    try {
      val entry = ZipEntry("a.txt")
      val text = "text".toByteArray()
      entry.method = ZipEntry.STORED
      entry.size = text.size.toLong()
      val crc32 = CRC32()
      crc32.update(text)
      entry.crc = crc32.value
      output.putNextEntry(entry)
      output.write(text)
      output.closeEntry()
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
    finally {
      output.close()
    }
    val a = addArtifact(archive("b.jar").extractedDir(path, ""))
    buildAll()
    assertOutput(a, directoryContent { zip("b.jar") { file("a.txt", "text") }})

    val jarPath = a.outputPath!! + "/b.jar"
    val zipFile = ZipFile(File(jarPath))
    zipFile.use {
      val entry = it.getEntry("a.txt")
      assertNotNull(entry)
      assertEquals(ZipEntry.STORED, entry.method)
    }
  }

  fun testBuildModuleBeforeArtifactIfSomeDirectoryInsideModuleOutputIsCopiedToArtifact() {
    val src = PathUtil.getParentPath(PathUtil.getParentPath(createFile("src/x/A.java", "package x; class A{}")))
    val module = addModule("m", src)
    val output = JpsJavaExtensionService.getInstance().getOutputDirectory(module, false)
    val artifact = addArtifact(root().dirCopy(File(output, "x").absolutePath))
    rebuildAllModulesAndArtifacts()
    assertOutput(module, directoryContent { dir("x") { file("A.class") }})
    assertOutput(artifact, directoryContent { file("A.class") })
  }

  fun testClearOutputOnRebuild() {
    val file = createFile("d/a.txt")
    val a = addArtifact(root().parentDirCopy(file))
    buildAll()
    createFileInArtifactOutput(a, "b.txt")
    buildAllAndAssertUpToDate()
    assertOutput(a, directoryContent {
      file("a.txt")
      file("b.txt")
    })

    rebuildAllModulesAndArtifacts()
    assertOutput(a, directoryContent {
      file("a.txt")
      file("b.txt")
    })
  }

  fun testDeleteOnlyOutputFileOnRebuildForArchiveArtifact() {
    val file = createFile("a.txt")
    val a = addArtifact(archive("a.jar").fileCopy(file))
    buildAll()
    createFileInArtifactOutput(a, "b.txt")
    buildAllAndAssertUpToDate()
    assertOutput(a, directoryContent {
      zip("a.jar") { file("a.txt") }
      file("b.txt")
    })

    rebuildAllModulesAndArtifacts()
    assertOutput(a, directoryContent {
      zip("a.jar") { file("a.txt") }
      file("b.txt")
    })
  }

  fun testDoNotCreateEmptyArchive() {
    val file = createFile("dir/a.txt")
    val a = addArtifact(archive("a.jar").parentDirCopy(file))
    delete(file)
    buildAll()
    assertEmptyOutput(a)
  }

  fun testDoNotCreateEmptyArchiveInsideArchive() {
    val file = createFile("dir/a.txt")
    val a = addArtifact(archive("a.jar").archive("inner.jar").parentDirCopy(file))
    delete(file)
    buildAll()
    assertEmptyOutput(a)
  }

  fun testDoNotCreateEmptyArchiveFromExtractedDirectory() {
    val jarFile = createXJarFile()
    val a = addArtifact("a", archive("a.jar").dir("dir").extractedDir(jarFile, "/xxx/"))
    buildAll()
    assertEmptyOutput(a)
  }

  fun testExtractNonExistentJarFile() {
    val a = addArtifact(root().extractedDir("this-file-does-not-exist.jar", "/"))
    buildAll()
    assertEmptyOutput(a)
  }

  fun testRepackNonExistentJarFile() {
    val a = addArtifact(archive("a.jar").extractedDir("this-file-does-not-exist.jar", "/").fileCopy(createFile("a.txt")))
    buildAll()
    assertOutput(a, directoryContent { zip("a.jar") {file("a.txt")}})
  }
}
