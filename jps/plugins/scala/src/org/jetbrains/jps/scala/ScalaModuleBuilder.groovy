package org.jetbrains.jps.scala

import org.jetbrains.jps.*

class ScalaModuleBuilder implements ModuleBuilder {
  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, ProjectBuilder projectBuilder) {
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
      compileScalaFacet(facet, projectBuilder, state)
    }
  }

  def compileScalaFacet(ScalaFacet facet, ProjectBuilder projectBuilder, ModuleBuildState state) {
    Library scalaCompiler = projectBuilder.project.libraries[facet.compilerLibraryName]
    if (scalaCompiler == null) {
      projectBuilder.error("Cannot find Scala compiler project library with name: ${facet.compilerLibraryName}");
      return
    }

    def ant = projectBuilder.binding.ant

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
