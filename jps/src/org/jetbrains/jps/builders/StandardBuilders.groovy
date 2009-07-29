package org.jetbrains.jps.builders

import org.jetbrains.jps.ModuleBuilder
import org.jetbrains.jps.Module
import org.jetbrains.jps.ModuleBuildState
import org.jetbrains.jps.Project

/**
 * @author max
 */
class JavacBuilder implements ModuleBuilder {

  def processModule(Module module, ModuleBuildState state) {
    if (state.sourceRoots.isEmpty()) return;

    def project = module.project
    def ant = project.binding.ant

    def sourceLevel = module["sourceLevel"]
    def targetLevel = module["targetLevel"]

    def params = [:]
    params.destdir = state.targetFolder
    if (sourceLevel != null) params.source = sourceLevel
    if (targetLevel != null) params.target = targetLevel
    
    ant.javac (params) {
      state.sourceRoots.each {
        src(path: it)
      }

      module.excludes.each { String root ->
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

  def processModule(Module module, ModuleBuildState state) {
    if (state.sourceRoots.isEmpty()) return;

    def project = module.project
    def ant = project.binding.ant

    ant.copy(todir: state.targetFolder) {
      state.sourceRoots.each { root ->
        fileset (dir : root) {
          patternset (refid: module["compiler.resources.id"])
          type (type: "file")
        }
      }
    }
  }
}

class GroovycBuilder implements ModuleBuilder {
  def GroovycBuilder(Project project) {
    project.binding.ant.taskdef (name: "groovyc", classname: "org.codehaus.groovy.ant.Groovyc")    
  }

  def processModule(Module module, ModuleBuildState state) {
    if (state.sourceRoots.isEmpty()) return;

    def project = module.project
    def ant = project.binding.ant

    ant.groovyc (destdir: state.targetFolder) {
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

class GroovyStubGenerator implements ModuleBuilder {

  def GroovyStubGenerator(Project project) {
    project.binding.ant.taskdef (name: "generatestubs", classname: "org.codehaus.groovy.ant.GenerateStubsTask")    
  }

  def processModule(Module module, ModuleBuildState state) {
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
