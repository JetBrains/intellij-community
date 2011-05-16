package org.jetbrains.jps.builders

import org.jetbrains.jps.*
import org.jetbrains.jps.builders.javacApi.Java16ApiCompilerRunner
import org.jetbrains.ether.dependencyView.Callbacks
import org.apache.tools.ant.BuildListener
import org.apache.tools.ant.BuildEvent
import java.util.regex.Pattern
import java.util.regex.Matcher
import org.jetbrains.ether.dependencyView.AntListener
import com.intellij.ant.Instrumenter
import org.jetbrains.ether.dependencyView.StringCache

/**
 * @author max
 */
class JavacBuilder implements ModuleBuilder, ModuleCycleBuilder {

  def preprocessModuleCycle(ModuleBuildState state, ModuleChunk moduleChunk, Project project) {
    doBuildModule(moduleChunk, state)
  }

  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, Project project) {
    doBuildModule(moduleChunk, state)
  }

  def doBuildModule(ModuleChunk module, ModuleBuildState state) {
    if (state.sourceRoots.isEmpty()) return;

    String sourceLevel = module["sourceLevel"]
    String targetLevel = module["targetLevel"]
    String customArgs = module["javac_args"]; // it seems javac_args property is not set, can we drop it?
    if (module.project.builder.useInProcessJavac) {
      String version = System.getProperty("java.version")
      if (true) {
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

    def javacOpts = module.project.props["compiler.javac.options"];
    def memHeapSize = javacOpts["MAXIMUM_HEAP_SIZE"] == null ? "512m" : javacOpts["MAXIMUM_HEAP_SIZE"] + "m";
    def boolean debugInfo = !"false".equals(javacOpts["DEBUGGING_INFO"]);
    def boolean nowarn = "true".equals(javacOpts["GENERATE_NO_WARNINGS"]);
    def boolean deprecation = !"false".equals(javacOpts["DEPRECATION"]);
    customArgs = javacOpts["ADDITIONAL_OPTIONS_STRING"];

    params.fork = "true"
    params.memoryMaximumSize = memHeapSize;
    params.debug = String.valueOf(debugInfo);
    params.nowarn = String.valueOf(nowarn);
    params.deprecation = String.valueOf(deprecation);
    params.verbose = "true"

    def javacExecutable = getJavacExecutable(module)
    if (javacExecutable != null) {
      params.executable = javacExecutable
    }

    def ant = module.project.binding.ant

    final BuildListener listener = new AntListener(state.targetFolder, state.sourceRoots, state.callback);

    ant.project.addBuildListener(listener);

    ant.javac(params) {
      if (customArgs) {
        compilerarg(line: customArgs)
      }

      if (state.sourceFiles != null) {
        List patterns = []

        state.sourceFiles.each {
          for (String root: state.sourceRoots) {
            if (it.startsWith(root) && it.endsWith(".java")) {
              patterns << it.substring(root.length() + 1)
              break;
            }
          }

          patterns.each {
            include(name: it)
          }
        }
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

    ant.project.removeBuildListener(listener);

    if (state.sourceFiles != null) {
      module.project.builder.listeners*.onJavaFilesCompiled(module, state.sourceFiles.size())
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

  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, Project project) {
    if (state.sourceRoots.isEmpty()) return;

    def ant = project.binding.ant

    state.sourceRoots.each {String root ->
      if (new File(root).exists()) {
        def target = state.targetFolder
        def prefix = moduleChunk.modules.collect { it.sourceRootPrefixes[root] }.find {it != null}
        if (prefix != null) {
          if (!(target.endsWith("/") || target.endsWith("\\"))) {
            target += "/"
          }
          target += prefix
        }

        ant.copy(todir: target) {
          fileset(dir: root) {
            patternset(refid: moduleChunk["compiler.resources.id"])
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
    project.taskdef(name: "groovyc", classname: "org.codehaus.groovy.ant.Groovyc")
  }

  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, Project project) {
    if (!GroovyFileSearcher.containGroovyFiles(state.sourceRoots)) return

    def ant = project.binding.ant

    final String destDir = state.targetFolder

    ant.touch(millis: 239) {
      fileset(dir: destDir) {
        include(name: "**/*.class")
      }
    }

    // unfortunately we have to disable fork here because of a bug in Groovyc task: it creates too long command line if classpath is large
    ant.groovyc(destdir: destDir /*, fork: "true"*/) {
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
    project.taskdef(name: "generatestubs", classname: "org.codehaus.groovy.ant.GenerateStubsTask")
  }

  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, Project project) {
    if (!GroovyFileSearcher.containGroovyFiles(state.sourceRoots)) return

    def ant = project.binding.ant

    String targetFolder = project.targetFolder
    File dir = new File(targetFolder != null ? targetFolder : ".", "___temp___")
    BuildUtil.deleteDir(project, dir.absolutePath)
    ant.mkdir(dir: dir)

    def stubsRoot = dir.getAbsolutePath()
    ant.generatestubs(destdir: stubsRoot) {
      state.sourceRoots.each {
        src(path: it)
      }

      include(name: "**/*.groovy")
      include(name: "**/*.java")

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

  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, Project project) {
    //if (project.getBuilder().useInProcessJavac)
    //  return;

    final StringBuffer cp = new StringBuffer()

    cp.append(state.targetFolder)
    cp.append(File.pathSeparator)

    state.classpath.each {
      cp.append(it)
      cp.append(File.pathSeparator)
    }

    final ClassLoader loader = Instrumenter.createClassLoader(cp.toString())

    if (!state.incremental) {
      new Object() {
        public void traverse(final File root) {
          final File[] files = root.listFiles();

          for (File f: files) {
            final String name = f.getName();

            if (name.endsWith(".class")) {
              Instrumenter.instrumentNotNull(f, loader)
            }
            else if (f.isDirectory()) {
              traverse(f)
            }
          }
        }
      }.traverse(new File(state.targetFolder))
    }
    else {
      final Collection<StringCache.S> classes = state.callback.getClassFiles()

      classes.each {
        Instrumenter.instrumentNotNull(new File(state.targetFolder + File.separator + it.value + ".class"), loader)
      }
    }
  }
}

class CustomTasksBuilder implements ModuleBuilder {
  List<ModuleBuildTask> tasks = []

  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, Project project) {
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