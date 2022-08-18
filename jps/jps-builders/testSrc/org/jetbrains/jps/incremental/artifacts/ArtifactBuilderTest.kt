// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.artifacts

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.directoryContent
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.zipFile
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.builders.CompileScopeTestBuilder
import org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.archive
import org.jetbrains.jps.incremental.artifacts.LayoutElementTestUtil.root
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.util.JpsPathUtil
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.inputStream

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
    val libDir = createDir("lib")
    directoryContent {
      zip("a.jar") { file("a.txt") }
    }.generate(File(libDir))
    val library = addProjectLibrary("lib", "$libDir/a.jar")
    val a = addArtifact(root().lib(library))
    buildAll()
    assertOutput(a, directoryContent { zip("a.jar") { file("a.txt") } })
  }

  fun testModuleOutput() {
    val file = createFile("src/A.java", "public class A {}")
    val module = addModule("a", PathUtil.getParentPath(file))
    val artifact = addArtifact(root().module(module))

    buildArtifacts(artifact)
    assertOutput(artifact, directoryContent { file("A.class") })
  }

  fun testModuleSources() {
    val file = createFile("src/A.java", "class A{}")
    val testFile = createFile("tests/ATest.java", "class ATest{}")
    val m = addModule("m", PathUtil.getParentPath(file))
    m.addSourceRoot(JpsPathUtil.pathToUrl(PathUtil.getParentPath(testFile)), JavaSourceRootType.TEST_SOURCE)
    val a = addArtifact(root().moduleSource(m))
    buildAll()
    assertOutput(a, directoryContent {
      file("A.java")
    })

    val b = createFile("src/B.java", "class B{}")

    buildAll()
    assertOutput(a, directoryContent {
      file("A.java")
      file("B.java")
    })

    delete(b)
    buildAll()
    assertOutput(a, directoryContent {
      file("A.java")
    })
  }

  fun testModuleSourcesWithPackagePrefix() {
    val file = createFile("src/A.java", "class A{}")
    val m = addModule("m", PathUtil.getParentPath(file))
    val sourceRoot = assertOneElement(m.sourceRoots)
    val typed = sourceRoot.asTyped(JavaSourceRootType.SOURCE)
    assertNotNull(typed)
    typed!!.properties.packagePrefix = "org.foo"

    val a = addArtifact(root().moduleSource(m))
    buildAll()
    assertOutput(a, directoryContent {
      dir("org") {
        dir("foo") {
          file("A.java")
        }
      }
    })
  }

  fun testCopyResourcesFromModuleOutput() {
    val file = createFile("src/a.xml", "")
    JpsJavaExtensionService.getInstance().getCompilerConfiguration(myProject).addResourcePattern("*.xml")
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

  private fun Path.checksum(): String = inputStream().buffered().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    DigestInputStream(input, digest).use {
      var bytesRead = 0
      val buffer = ByteArray(1024 * 8)
      while (bytesRead != -1) {
        bytesRead = it.read(buffer)
      }
    }
    Base64.getEncoder().encodeToString(digest.digest())
  }

  fun `test jars build reproducibility`() {
    myBuildParams[GlobalOptions.BUILD_DATE_IN_SECONDS] = (System.currentTimeMillis() / 1000).toString()
    val jar = root().archive("a.jar")
      .extractedDir(createXJarFile(), "/")
      .dir("META-INF").fileCopy(createFile("src/MANIFEST.MF"))
      .let { addArtifact("a", it).outputPath }
      ?.let { Paths.get(it).resolve("a.jar") }
    requireNotNull(jar)
    val checksums = (1..2).map {
      // sleeping more than a second ensures different last modification time
      // for the next iteration jar if the build date isn't provided or ignored
      TimeUnit.SECONDS.sleep(2)
      FileUtil.delete(jar.parent)
      assert(!Files.exists(jar))
      buildAll()
      assert(Files.exists(jar))
      jar.checksum()
    }.distinct()
    assert(checksums.count() == 1)
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
    val zipFile = zipFile {
      dir("dir") {
        file("file.txt", "text")
      }
    }.generateInTempDir()
    return zipFile.toAbsolutePath().systemIndependentPath
  }

  fun testSelfIncludingArtifact() {
    val a = addArtifact("a", root().fileCopy(createFile("a.txt")))
    LayoutElementTestUtil.addArtifactToLayout(a, a)
    assertBuildFailed(a)
  }

  fun testCircularInclusion() {
    val a = addArtifact("a", root().fileCopy(createFile("a.txt")))
    val b = addArtifact("b", root().fileCopy(createFile("b.txt")))
    LayoutElementTestUtil.addArtifactToLayout(a, b)
    LayoutElementTestUtil.addArtifactToLayout(b, a)
    assertBuildFailed(a)
    assertBuildFailed(b)
  }

  fun testArtifactContainingSelfIncludingArtifact() {
    val c = addArtifact("c", root().fileCopy(createFile("c.txt")))
    val a = addArtifact("a", root().artifact(c).fileCopy(createFile("a.txt")))
    LayoutElementTestUtil.addArtifactToLayout(a, a)
    val b = addArtifact("b", root().artifact(a).fileCopy(createFile("b.txt")))

    buildArtifacts(c)
    assertBuildFailed(b)
    assertBuildFailed(a)
  }

  fun testArtifactContainingSelfIncludingArtifactWithoutOutput() {
    val a = addArtifact("a", root().fileCopy(createFile("a.txt")))
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

  fun testProperlyReportValueWithInvalidCrcInRepackedFile() {
    val corruptedJar = PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/output/corruptedJar/incorrect-crc.jar")!!.absolutePath
    val a = addArtifact(archive("a.jar").extractedDir(corruptedJar, ""))
    val result = doBuild(CompileScopeTestBuilder.rebuild().artifacts(a))
    result.assertFailed()
    val message = result.getMessages(BuildMessage.Kind.ERROR).first()
    assertTrue(message.messageText, message.messageText.contains("incorrect-crc.jar"))
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
