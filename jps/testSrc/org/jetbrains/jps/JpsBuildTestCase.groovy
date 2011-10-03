package org.jetbrains.jps

import junit.framework.TestCase
import org.codehaus.gant.GantBinding
import org.jetbrains.ether.ProjectWrapper
import org.jetbrains.jps.idea.IdeaProjectLoader
import org.jetbrains.jps.util.FileSystemItem
import org.jetbrains.jps.util.TempFiles

/**
 * @author nik
 */
abstract class JpsBuildTestCase extends TestCase {
  private TempFiles myTempFiles;

  @Override
  protected void setUp() {
    myTempFiles = new TempFiles();
  }

  @Override
  protected void tearDown() {
    myTempFiles.cleanup();
  }

  def doTest(String projectPath, Closure initProject, Closure expectedOutput) {
    doTest(projectPath, [:], initProject, expectedOutput)
  }

  def doTest(String projectPath, Map<String, String> pathVariables, Closure initProject, Closure expectedOutput) {
    Project project = buildAll(projectPath, pathVariables, initProject)
    assertOutput(project, project.targetFolder, expectedOutput);
  }

  def protected assertOutput(Project project, String targetFolder, Closure expectedOutput) {
    def root = new FileSystemItem(name: "<root>")
    initFileSystemItem(root, expectedOutput)
    root.assertDirectoryEqual(new File(targetFolder), "")
  }

  def protected buildAll(String projectPath, Map<String, String> pathVariables, Closure initProject) {
    def binding = new GantBinding()
    binding.includeTool << Jps
    ProjectWrapper pw = ProjectWrapper.load(binding, projectPath, null, pathVariables, false)
    Project project = pw.getProject()
    def target = createTempDir()
    project.targetFolder = target.absolutePath
    initProject(project)
    pw.clean()
    pw.rebuild()
    pw.buildArtifacts()
    pw.deleteTempFiles()
    return project
  }

  protected Project loadProject(String projectPath, Map<String, String> pathVariables) {
    return loadProject(projectPath, pathVariables, {})
  }

  protected Project loadProject(String projectPath, Map<String, String> pathVariables, Closure initGlobal) {
    def binding = new GantBinding()
    binding.includeTool << Jps
    def project = new Project(binding)
    initGlobal(project)
    IdeaProjectLoader.loadFromPath(project, projectPath, pathVariables, null)
    return project
  }

  def initFileSystemItem(FileSystemItem item, Closure initializer) {
    def meta = new InitializingExpando()
    meta.dir = {String name, Closure content ->
      def dir = new FileSystemItem(name: name, directory: true)
      initFileSystemItem(dir, content)
      item << dir
    }
    meta.archive = {String name, Closure content ->
      def archive = new FileSystemItem(name: name, archive: true)
      initFileSystemItem(archive, content)
      item << archive
    }
    meta.file = {Object[] args ->
      item << new FileSystemItem(name: args[0], content: args.length > 1 ? args[1] : null)
    }

    initializer.delegate = meta
    initializer.setResolveStrategy Closure.DELEGATE_FIRST
    initializer()
  }

  def File createTempDir() {
    return myTempFiles.createTempDir();
  }

  def File createTempFile() {
    return myTempFiles.createTempFile();
  }
}
