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
package org.jetbrains.jps.builders.rebuild
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.TestFileSystemBuilder
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.model.java.JpsJavaExtensionService
/**
 * @author nik
 */
abstract class JpsRebuildTestCase extends JpsBuildTestCase {
  protected File myOutputDirectory;

  @Override
  protected void setUp() {
    super.setUp()
    addJdk("1.6")
  }

  def doTest(String projectPath, Closure expectedOutput) {
    doTest(projectPath, [:], expectedOutput)
  }

  def doTest(String projectPath, Map<String, String> pathVariables, Closure expectedOutput) {
    loadAndRebuild(projectPath, pathVariables)
    assertOutput(getOrCreateOutputDirectory().getAbsolutePath(), expectedOutput);
  }

  def protected assertOutput(String targetFolder, Closure expectedOutput) {
    def root = TestFileSystemBuilder.fs()
    initFileSystemItem(root, expectedOutput)
    root.build().assertDirectoryEqual(new File(FileUtil.toSystemDependentName(targetFolder)))
  }

  protected void loadAndRebuild(String projectPath, Map<String, String> pathVariables) {
    loadProject(projectPath, pathVariables)
    rebuild()
  }

  protected void rebuild() {
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myProject).outputUrl = JpsPathUtil.pathToUrl(FileUtil.toSystemIndependentName(getOrCreateOutputDirectory().getAbsolutePath()))
    rebuildAll()
  }

  private File getOrCreateOutputDirectory() {
    if (myOutputDirectory == null) {
      myOutputDirectory = FileUtil.createTempDirectory("jps-build-output", "")
    }
    myOutputDirectory
  }

  @Override
  protected void addPathVariables(Map<String, String> pathVariables) {
    pathVariables.put("ARTIFACTS_OUT", FileUtil.toSystemIndependentName(getOrCreateOutputDirectory().absolutePath) + "/artifacts")
  }

  @Override
  protected String getTestDataRootPath() {
    return PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/output").absolutePath
  }

  def initFileSystemItem(TestFileSystemBuilder item, Closure initializer) {
    def meta = new Expando()
    meta.dir = {String name, Closure content ->
      initFileSystemItem(item.dir(name), content)
    }
    meta.archive = {String name, Closure content ->
      initFileSystemItem(item.archive(name), content)
    }
    meta.file = {Object[] args ->
      item.file((String)args[0], (String)args.length > 1 ? args[1] : null)
    }

    initializer.delegate = meta
    initializer.setResolveStrategy Closure.DELEGATE_FIRST
    initializer()
  }

  def File createTempFile() {
    return FileUtil.createTempFile("jps-build-file", "");
  }
}
