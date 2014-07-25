package org.jetbrains.jps.builders.rebuild;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.TestFileSystemBuilder;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.LinkedHashMap;
import kotlin.properties.Delegates
import com.intellij.util.io.TestFileSystemItem

/**
 * @author nik
 */
public abstract class JpsRebuildTestCase: JpsBuildTestCase() {
  protected val myOutputDirectory: File by Delegates.lazy {
    FileUtil.createTempDirectory("jps-build-output", "")
  }

  override fun setUp() {
    super.setUp()
    addJdk("1.6");
  }

  fun doTest(projectPath: String, expectedOutput: TestFileSystemItem) {
    doTest(projectPath, LinkedHashMap<String, String>(), expectedOutput);
  }

  fun doTest(projectPath: String, pathVariables: Map<String, String>, expectedOutput: TestFileSystemItem) {
    loadAndRebuild(projectPath, pathVariables);
    assertOutput(myOutputDirectory.getAbsolutePath(), expectedOutput);
  }

  fun assertOutput(targetFolder: String, expectedOutput: TestFileSystemItem) {
    expectedOutput.assertDirectoryEqual(File(FileUtil.toSystemDependentName(targetFolder)));
  }

  fun loadAndRebuild(projectPath: String, pathVariables: Map<String, String>) {
    loadProject(projectPath, pathVariables);
    rebuild();
  }

  fun rebuild() {
    JpsJavaExtensionService.getInstance()!!.getOrCreateProjectExtension(myProject)
      .setOutputUrl(JpsPathUtil.pathToUrl(FileUtil.toSystemIndependentName(myOutputDirectory.getAbsolutePath())));
    rebuildAll();
  }

  override fun getAdditionalPathVariables(): MutableMap<String, String> =
    hashMapOf("ARTIFACTS_OUT" to FileUtil.toSystemIndependentName(myOutputDirectory.getAbsolutePath()) + "/artifacts")

  protected override fun getTestDataRootPath(): String {
    return PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/output")!!.getAbsolutePath();
  }
}

fun fs(init: TestFileSystemBuilderBuilder.() -> Unit): TestFileSystemItem {
  val builder = TestFileSystemBuilder.fs()
  TestFileSystemBuilderBuilder(builder).init()
  return builder.build()
}

class TestFileSystemBuilderBuilder(private val current: TestFileSystemBuilder) {
  fun file(name: String) {
    current.file(name)
  }

  fun file(name: String, content: String) {
    current.file(name, content)
  }

  fun dir(name: String, init: TestFileSystemBuilderBuilder.() -> Unit) {
    val dir = current.dir(name)
    TestFileSystemBuilderBuilder(dir).init()
    dir.end()
  }

  fun archive(name: String, init: TestFileSystemBuilderBuilder.() -> Unit) {
    val dir = current.archive(name)
    TestFileSystemBuilderBuilder(dir).init()
    dir.end()
  }
}