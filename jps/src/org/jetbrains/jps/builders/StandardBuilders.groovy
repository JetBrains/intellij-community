package org.jetbrains.jps.builders

import org.jetbrains.jps.*
import org.jetbrains.jps.builders.javacApi.Java16ApiCompilerRunner

/**
 * @author max
 */
class JavacBuilder implements ModuleBuilder {

  def processModule(ModuleChunk module, ModuleBuildState state) {
    if (state.sourceRoots.isEmpty()) return;

    String sourceLevel = module["sourceLevel"]
    String targetLevel = module["targetLevel"]
    String customArgs = module["javac_args"]
    if (module.project.builder.useInProcessJavac) {
      String version = System.getProperty("java.version")
      if (sourceLevel == null || version.startsWith(sourceLevel)) {
        if (Java16ApiCompilerRunner.compile(module, state, sourceLevel, targetLevel, customArgs)) {
          return
        }
      }
      else {
        module.project.info("In-process Javac won't be used for '${module.name}', because Java version ($version) doesn't match to source level ($sourceLevel)")
      }
    }

    def params = [:]
    params.destdir = state.targetFolder
    if (sourceLevel != null) params.source = sourceLevel
    if (targetLevel != null) params.target = targetLevel

    params.memoryMaximumSize = "512m"
    params.fork = "true"
    params.debug = "on"

    def javacExecutable = getJavacExecutable(module)
    if (javacExecutable != null) {
      params.executable = javacExecutable
    }

    def ant = module.project.binding.ant
    ant.javac(params) {
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

  private String getJavacExecutable(ModuleChunk module) {
    def customJavac = module["javac"]
    def jdk = module.getSdk()
    if (customJavac != null) {
      return customJavac
    }
    else if (jdk instanceof JavaSdk) {
      return jdk.getJavacExecutable()
    }
    return null
  }
}

class ResourceCopier implements ModuleBuilder {

  def processModule(ModuleChunk chunk, ModuleBuildState state) {
    if (state.sourceRoots.isEmpty()) return;

    def project = chunk.project
    def ant = project.binding.ant

    state.sourceRoots.each {String root ->
      if (new File(root).exists()) {
        def target = state.targetFolder
        def prefix = chunk.modules.collect { it.sourceRootPrefixes[root] }.find {it != null}
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
  }
}

class GroovycBuilder implements ModuleBuilder {
  def GroovycBuilder(Project project) {
    project.taskdef (name: "groovyc", classname: "org.codehaus.groovy.ant.Groovyc")
  }

  def processModule(ModuleChunk module, ModuleBuildState state) {
    if (!GroovyFileSearcher.containGroovyFiles(state.sourceRoots)) return

    def project = module.project
    def ant = project.binding.ant

    final String destDir = state.targetFolder

    ant.touch(millis: 239) {
      fileset(dir: destDir) {
        include(name: "**/*.class")
      }
    }

    ant.groovyc(destdir: destDir, fork: "true") {
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
    if (!GroovyFileSearcher.containGroovyFiles(state.sourceRoots)) return

    def project = module.project
    def ant = project.binding.ant

    String targetFolder = project.targetFolder
    File dir = new File(targetFolder != null ? targetFolder : ".", "___temp___")
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

    ant.jb_instrumentations(destdir: state.targetFolder, failonerror: "false", includeAntRuntime: "false") {
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

class CustomTasksBuilder implements ModuleBuilder {
  List<ModuleBuildTask> tasks = []

  def processModule(ModuleChunk moduleChunk, ModuleBuildState state) {
    moduleChunk.modules.each {Module module ->
      tasks*.perform(module, state.targetFolder)
    }
  }

  def registerTask(String moduleName, Closure task) {
    tasks << ({Module module, String outputFolder ->
      if (module.name == moduleName) {
        task(module, outputFolder)
      }
    } as ModuleBuildTask)
  }
}