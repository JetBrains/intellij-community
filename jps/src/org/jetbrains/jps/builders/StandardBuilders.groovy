package org.jetbrains.jps.builders

import org.jetbrains.jps.ModuleBuildState
import org.jetbrains.jps.ModuleBuilder
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.Project

/**
 * @author max
 */
class JavacBuilder implements ModuleBuilder {

  def processModule(ModuleChunk module, ModuleBuildState state) {
    if (state.sourceRoots.isEmpty()) return;

    def project = module.project
    def ant = project.binding.ant

    def sourceLevel = module["sourceLevel"]
    def targetLevel = module["targetLevel"]

    def params = [:]
    params.destdir = state.targetFolder
    if (sourceLevel != null) params.source = sourceLevel
    if (targetLevel != null) params.target = targetLevel

    params.memoryMaximumSize = "512m"
    params.fork = "true"
    params.debug = "on"

    def customJavac = module["javac"]
    if (customJavac != null) {
      params.executable = customJavac
    }

    def customArgs = module["javac_args"]
    ant.javac (params) {
      if (customArgs) {
        compilerarg(line: customArgs)
      }

      state.sourceRoots.each {
        src(path: it)
      }

      state.excludes.each { String root ->
        state.sourceRoots.each {String src ->
          if (root.startsWith("${src}/")) {
            exclude(name: "${root.substring(src.length() + 1)}/**")
          }
        }
      }

      classpath {
        state.classpath.each {
          pathelement(location: it)
        }
      }
    }
  }
}

class ResourceCopier implements ModuleBuilder {

  def processModule(ModuleChunk chunk, ModuleBuildState state) {
    if (state.sourceRoots.isEmpty()) return;

    def project = chunk.project
    def ant = project.binding.ant

    chunk.modules.each { module ->
      def rootProcessor = {String root ->
        if (new File(root).exists()) {
          def target = state.targetFolder
          def prefix = module.sourceRootPrefixes[root]
          if (prefix != null) {
            if (!(target.endsWith("/") || target.endsWith("\\"))) {
              target += "/"
            }
            target += prefix
          }

          ant.copy(todir: target) {
            fileset(dir: root) {
              patternset(refid: chunk["compiler.resources.id"])
              type(type: "file")
            }
          }
        }
        else {
          project.warning("$root doesn't exist")
        }
      }

      module.sourceRoots.each (rootProcessor)
      module.testRoots.each (rootProcessor)
    }
  }
}

class GroovycBuilder implements ModuleBuilder {
  def GroovycBuilder(Project project) {
    project.taskdef (name: "groovyc", classname: "org.codehaus.groovy.ant.Groovyc")
  }

  def processModule(ModuleChunk module, ModuleBuildState state) {
    if (state.sourceRoots.isEmpty()) return;

    def project = module.project
    def ant = project.binding.ant

    final String destDir = state.targetFolder

    ant.touch(millis: 239) {
      fileset(dir: destDir) {
        include(name: "**/*.class")
      }
    }

    ant.groovyc(destdir: destDir) {
      state.sourceRoots.each {
        src(path: it)
      }

      include(name: "**/*.groovy")

      classpath {
        state.classpath.each {
          pathelement(location: it)
        }

        pathelement(location: destDir) // Includes classes generated there by javac compiler
      }
    }

    ant.touch() {
      fileset(dir: destDir) {
        include(name: "**/*.class")
      }
    }
  }
}

class GroovyStubGenerator implements ModuleBuilder {

  def GroovyStubGenerator(Project project) {
    project.taskdef (name: "generatestubs", classname: "org.codehaus.groovy.ant.GenerateStubsTask")
  }

  def processModule(ModuleChunk module, ModuleBuildState state) {
    if (state.sourceRoots.isEmpty()) return

    def project = module.project
    def ant = project.binding.ant

    File dir = new File(project.targetFolder, "___temp___")
    ant.delete(dir: dir)
    ant.mkdir(dir: dir)

    def stubsRoot = dir.getAbsolutePath()
    ant.generatestubs(destdir: stubsRoot) {
      state.sourceRoots.each {
        src(path: it)
      }

      include (name: "**/*.groovy")
      include (name: "**/*.java")

      classpath {
        state.classpath.each {
          pathelement(location: it)
        }
      }
    }

    state.sourceRoots << stubsRoot
    state.tempRootsToDelete << stubsRoot
  }

}

class JetBrainsInstrumentations implements ModuleBuilder {

  def JetBrainsInstrumentations(Project project) {
    project.taskdef(name: "jb_instrumentations", classname: "com.intellij.ant.InstrumentIdeaExtensions")
  }

  def processModule(ModuleChunk module, ModuleBuildState state) {
    def project = module.project
    def ant = project.binding.ant

    ant.jb_instrumentations(destdir: state.targetFolder, failonerror: "false") {
      state.sourceRoots.each {
        src(path: it)
      }

      classpath {
        state.classpath.each {
          pathelement(location: it)
        }
      }
    }
  }
}
