package org.jetbrains.jps

import junit.framework.TestCase
import org.codehaus.gant.GantBinding
import org.jetbrains.jps.idea.IdeaProjectLoader
import org.jetbrains.jps.util.FileSystemItem
import org.jetbrains.jps.util.FileUtil;

/**
 * @author nik
 */
abstract class JpsBuildTestCase extends TestCase {
  
  def doTest(String projectPath, Closure initProject, Closure expectedOutput) {
    doTest(projectPath, [:], initProject, expectedOutput)
  }

  def doTest(String projectPath, Map<String, String> pathVariables, Closure initProject, Closure expectedOutput) {
    Project project = buildAll(projectPath, pathVariables, initProject)

    def root = new FileSystemItem(name: "<root>")
    initFileSystemItem(root, expectedOutput)
    root.assertDirectoryEqual(new File(project.targetFolder), "");
  }

  def protected buildAll(String projectPath, Map<String, String> pathVariables, Closure initProject) {
    def binding = new GantBinding()
    binding.includeTool << Jps
    def project = new Project(binding)
    IdeaProjectLoader.loadFromPath(project, projectPath, pathVariables)
    initProject(project)
    def target = FileUtil.createTempDirectory("targetDir")
    project.targetFolder = target.absolutePath
    project.clean()
    project.makeAll()
    project.buildArtifacts()
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

}
