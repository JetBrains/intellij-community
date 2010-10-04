package org.jetbrains.jps.gwt

import org.jetbrains.jps.ModuleBuildState
import org.jetbrains.jps.ModuleBuilder
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.Project
import org.jetbrains.jps.builders.BuildUtil

/**
 * @author nik
 */
class GwtModuleBuilder implements ModuleBuilder {
  def processModule(ModuleChunk chunk, ModuleBuildState state) {
    List<GwtFacet> facets = []
    chunk.modules.each {
      it.facets.values().each {
        if (it instanceof GwtFacet) {
          facets << it
        }
      }
    }

    if (facets.isEmpty()) return

    def project = chunk.project
    facets.each {GwtFacet facet ->
      compileGwtFacet(facet, project, state)
    }
  }

  def compileGwtFacet(GwtFacet facet, Project project, ModuleBuildState state) {
    if (facet.tempOutputDir != null) return

    if (!new File(facet.sdkPath).exists()) {
      project.error("GWT SDK directory $facet.sdkPath not found")
    }

    List<String> gwtModules = GwtModulesSearcher.findGwtModules(facet.module.sourceRoots)
    if (gwtModules.isEmpty()) {
      project.info("No GWT modules found in GWT facet in ${facet.module} module")
      return
    }

    String baseDir = project.targetFolder != null ? project.targetFolder : "."
    String dirName = BuildUtil.suggestFileName(facet.module.name)
    String outputDir = new File(baseDir, "__temp_gwt_output_$dirName").absolutePath
    facet.tempOutputDir = outputDir

    def ant = project.binding.ant
    ant.delete(dir: outputDir)
    ant.mkdir(dir: outputDir)

    gwtModules.each {String moduleName ->
      project.stage("Compiling GWT Module")
      ant.java(fork: "true", classname: "com.google.gwt.dev.Compiler") {
        jvmarg(line: "-Xmx${facet.compilerMaxHeapSize}m")
        if (!facet.additionalCompilerParameters.isEmpty()) {
          jvmarg(line: facet.additionalCompilerParameters)
        }
        classpath {
          pathelement(location: "${facet.sdkPath}/gwt-dev.jar")
          state.sourceRoots.each {
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
}
