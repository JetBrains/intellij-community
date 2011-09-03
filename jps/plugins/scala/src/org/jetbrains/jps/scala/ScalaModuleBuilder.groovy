package org.jetbrains.jps.scala

import org.jetbrains.jps.*

class ScalaModuleBuilder implements ModuleBuilder {
  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, Project project) {
    List<ScalaFacet> facets = []
    moduleChunk.modules.each {
      it.facets.values().each {
        if (it instanceof ScalaFacet) {
          facets << it
        }
      }
    }

    if (facets.isEmpty()) return

    facets.each {ScalaFacet facet ->
      compileScalaFacet(facet, project, state)
    }
  }

  def compileScalaFacet(ScalaFacet facet, Project project, ModuleBuildState state) {
    Library scalaCompiler = project.libraries[facet.compilerLibraryName]
    if (scalaCompiler == null) {
      project.error("Cannot find Scala compiler project library with name: ${facet.compilerLibraryName}");
      return
    }

    def ant = project.binding.ant

    if (!ant.hasProperty("scalac")) {
      ant.taskdef(name: "scalac", classname: "scala.tools.ant.Scalac") {
        classpath {
          scalaCompiler.classpath.each {
            pathelement(location: it)
          }
        }
      }
    }

    ant.scalac(destdir: state.targetFolder) {
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
