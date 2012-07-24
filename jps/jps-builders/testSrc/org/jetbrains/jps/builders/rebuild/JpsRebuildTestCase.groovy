package org.jetbrains.jps.builders.rebuild

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.TestFileSystemBuilder
import org.jetbrains.jps.JpsPathUtil
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.incremental.AllProjectScope
import org.jetbrains.jps.incremental.BuildLoggingManager
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLoggerImpl
import org.jetbrains.jps.incremental.java.JavaBuilderLoggerImpl
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.java.JpsJavaExtensionService
/**
 * @author nik
 */
abstract class JpsRebuildTestCase extends JpsBuildTestCase {
  protected File myOutputDirectory;

  @Override
  protected void setUp() {
    super.setUp()
    initJdk("1.6")
  }

  def doTest(String projectPath, Closure initProject, Closure expectedOutput) {
    doTest(projectPath, [:], initProject, expectedOutput)
  }

  def doTest(String projectPath, Map<String, String> pathVariables, Closure initProject, Closure expectedOutput) {
    loadAndRebuild(projectPath, pathVariables, initProject)
    assertOutput(getOrCreateOutputDirectory().getAbsolutePath(), expectedOutput);
  }

  def protected assertOutput(String targetFolder, Closure expectedOutput) {
    def root = TestFileSystemBuilder.fs()
    initFileSystemItem(root, expectedOutput)
    root.build().assertDirectoryEqual(new File(FileUtil.toSystemDependentName(targetFolder)))
  }

  protected void loadAndRebuild(String projectPath, Map<String, String> pathVariables, Closure initProject) {
    loadProject(projectPath, pathVariables)
    if (initProject != null) {
      initProject(myProject, null)
    }
    rebuild()
  }

  protected void rebuild() {
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myJpsProject).outputUrl = JpsPathUtil.pathToUrl(FileUtil.toSystemIndependentName(getOrCreateOutputDirectory().getAbsolutePath()))
    def descriptor = createProjectDescriptor(new BuildLoggingManager(new ArtifactBuilderLoggerImpl(), new JavaBuilderLoggerImpl()))
    try {
      def scope = new AllProjectScope(myProject, myJpsProject, new HashSet<JpsArtifact>(JpsArtifactService.getInstance().getArtifacts(myJpsProject)), true)
      doBuild(createBuilder(descriptor), scope, false, false, true)
    }
    finally {
      descriptor.release();
    }
  }

  private File getOrCreateOutputDirectory() {
    if (myOutputDirectory == null) {
      myOutputDirectory = FileUtil.createTempDirectory("jps-build-output", "")
    }
    myOutputDirectory
  }

  @Override
  protected Map<String, String> addPathVariables(Map<String, String> pathVariables) {
    def map = new HashMap<String, String>(pathVariables)
    map.put("ARTIFACTS_OUT", FileUtil.toSystemIndependentName(getOrCreateOutputDirectory().absolutePath) + "/artifacts")
    return map
  }

  @Override
  protected String getTestDataRootPath() {
    return PathManagerEx.getCommunityHomePath() + "/jps/jps-builders/testData/output"
  }

  protected void loadProject(String projectPath, Map<String, String> pathVariables, Closure initGlobal) {
    initGlobal(myProject)
    loadProject(projectPath, pathVariables)
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

  def File createTempDir() {
    return FileUtil.createTempDirectory("jps-build", "");
  }

  def File createTempFile() {
    return FileUtil.createTempFile("jps-build-file", "");
  }
}
