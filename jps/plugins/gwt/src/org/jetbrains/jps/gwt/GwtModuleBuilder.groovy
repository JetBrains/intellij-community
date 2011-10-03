package org.jetbrains.jps.gwt

import org.jetbrains.jps.ModuleBuildState
import org.jetbrains.jps.ModuleBuilder
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.LayoutElement
import org.jetbrains.jps.artifacts.ArtifactLayoutElement
import org.jetbrains.jps.builders.BuildUtil

/**
 * @author nik
 */
class GwtModuleBuilder implements ModuleBuilder {
  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, Project project) {
    List<GwtFacet> facets = []
    moduleChunk.modules.each {
      it.facets.values().each {
        if (it instanceof GwtFacet) {
          facets << it
        }
      }
    }

    if (facets.isEmpty()) return

    facets.each {GwtFacet facet ->
      compileGwtFacet(facet, project, state)
    }
  }

  def compileGwtFacet(GwtFacet facet, Project project, ModuleBuildState state) {
    if (!isIncludedInArtifact(facet, project)) {
      project.info("GWT Facet in module '${facet.module.name}' isn't included in artifacts so GWT compiler won't be called")
      return
    }

    if (facet.tempOutputDir != null) return

    if (!new File(facet.sdkPath).exists()) {
      project.error("GWT SDK directory $facet.sdkPath not found")
    }

    List<String> gwtModules = GwtModulesSearcher.findGwtModules(facet.module.sourceRoots)
    if (gwtModules.isEmpty()) {
      project.info("No GWT modules found in GWT facet in ${facet.module} module")
      return
    }

    String outputDir = project.builder.getTempDirectoryPath("GWT_Output_$facet.module.name")
    facet.tempOutputDir = outputDir

    def ant = project.binding.ant
    BuildUtil.deleteDir(project, outputDir)
    ant.mkdir(dir: outputDir)

    gwtModules.each {String moduleName ->
      project.stage("Compiling GWT Module")
      ant.java(fork: "true", classname: "com.google.gwt.dev.Compiler", failonerror: "true") {
        jvmarg(line: "-Xmx${facet.compilerMaxHeapSize}m")
        if (!facet.additionalCompilerParameters.isEmpty()) {
          jvmarg(line: facet.additionalCompilerParameters)
        }
        classpath {
          pathelement(location: "${facet.sdkPath}/gwt-dev.jar")
          state.sourceRootsFromModuleWithDependencies.each {
            pathelement(location: it)
          }
          state.classpath.each {
            pathelement(location: it)
          }
        }
        arg(value: "-war")
        arg(value: outputDir)
        arg(value: "-style")
        arg(value: facet.scriptOutputStyle)
        arg(value: moduleName)
      }
    }
  }

  private boolean isIncludedInArtifact(GwtFacet gwtFacet, Project project) {
    boolean included = false
    project.artifacts.values()*.rootElement*.process(project) {LayoutElement element ->
      if (element instanceof GwtCompilerOutputElement
          && ((GwtCompilerOutputElement)element).findFacet(project) == gwtFacet) {
        included = true
      }
      return !(element instanceof ArtifactLayoutElement)
    }
    return included
  }
}
