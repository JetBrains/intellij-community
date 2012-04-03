package org.jetbrains.jps.builders

import com.intellij.ant.InstrumentationUtil
import com.intellij.ant.InstrumentationUtil.FormInstrumenter
import com.intellij.ant.PrefixedPath
import com.intellij.compiler.instrumentation.InstrumentationClassFinder
import org.jetbrains.jps.builders.javacApi.Java16ApiCompilerRunner
import org.jetbrains.jps.*

/**
 * @author max
 */
class JavacBuilder implements ModuleBuilder, ModuleCycleBuilder {

  def preprocessModuleCycle(ModuleBuildState state, ModuleChunk moduleChunk, ProjectBuilder projectBuilder) {
    doBuildModule(moduleChunk, state, projectBuilder)
  }

  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, ProjectBuilder projectBuilder) {
    doBuildModule(moduleChunk, state, projectBuilder)
  }

  def doBuildModule(ModuleChunk module, ModuleBuildState state, ProjectBuilder projectBuilder) {
    if (state.sourceRoots.isEmpty()) return;

    String sourceLevel = module.languageLevel
    String targetLevel = module.languageLevel
    String customArgs = module["javac_args"]; // it seems javac_args property is not set, can we drop it?
    if (projectBuilder.useInProcessJavac) {
      String version = System.getProperty("java.version")
      if (true) {
        if (Java16ApiCompilerRunner.compile(module, projectBuilder, state, sourceLevel, targetLevel, customArgs)) {
          return
        }
      }
      else {
        projectBuilder.info("In-process Javac won't be used for '${module.name}', because Java version ($version) doesn't match to source level ($sourceLevel)")
      }
    }

    def params = [:]
    params.destdir = state.targetFolder
    if (sourceLevel != null) params.source = sourceLevel
    if (targetLevel != null) params.target = targetLevel

    def javacOpts = module.project.compilerConfiguration.javacOptions;
    def memHeapSize = javacOpts["MAXIMUM_HEAP_SIZE"] == null ? "512m" : javacOpts["MAXIMUM_HEAP_SIZE"] + "m";
    def boolean debugInfo = !"false".equals(javacOpts["DEBUGGING_INFO"]);
    def boolean nowarn = "true".equals(javacOpts["GENERATE_NO_WARNINGS"]);
    def boolean deprecation = !"false".equals(javacOpts["DEPRECATION"]);
    customArgs = javacOpts["ADDITIONAL_OPTIONS_STRING"];
    if ((customArgs == null || customArgs.indexOf("-encoding") == -1) && module.project.projectCharset != null) {
      params.encoding = module.project.projectCharset;
    }

    params.fork = "true"
    params.memoryMaximumSize = memHeapSize;
    params.debug = String.valueOf(debugInfo);
    params.nowarn = String.valueOf(nowarn);
    params.deprecation = String.valueOf(deprecation);

    def javacExecutable = getJavacExecutable(module)
    if (javacExecutable != null) {
      params.executable = javacExecutable
    }

    def ant = projectBuilder.binding.ant

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
  private boolean resourcePatternInitialized

  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, ProjectBuilder projectBuilder) {
    if (!resourcePatternInitialized) {
      CompilerConfiguration configuration = projectBuilder.project.compilerConfiguration
      projectBuilder.binding.ant.patternset(id: "compiler.resources") {
        configuration.resourceIncludePatterns.each { include(name: it)}
        configuration.resourceExcludePatterns.each { exclude(name: it)}
      }
      resourcePatternInitialized = true
    }

    if (state.sourceRoots.isEmpty()) return;

    def ant = projectBuilder.binding.ant

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
            patternset(refid: "compiler.resources")
            type(type: "file")
            state.excludes.each { String excludedRoot ->
              if (excludedRoot.startsWith("${root}/")) {
                exclude(name: "${excludedRoot.substring(root.length() + 1)}/**")
              }
            }
          }
        }
      }
      else {
        projectBuilder.warning("$root doesn't exist")
      }
    }
  }
}

class GroovycBuilder implements ModuleBuilder {
  def GroovycBuilder(org.jetbrains.jps.ProjectBuilder projectBuilder) {
    projectBuilder.binding.ant.taskdef(name: "groovyc", classname: "org.codehaus.groovy.ant.Groovyc")
  }

  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, ProjectBuilder projectBuilder) {
    if (!GroovyFileSearcher.containGroovyFiles(state.sourceRoots)) return

    def ant = projectBuilder.binding.ant

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

  def GroovyStubGenerator(org.jetbrains.jps.ProjectBuilder projectBuilder) {
    projectBuilder.binding.ant.taskdef(name: "generatestubs", classname: "org.codehaus.groovy.ant.GenerateStubsTask")
  }

  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, ProjectBuilder projectBuilder) {
    if (!GroovyFileSearcher.containGroovyFiles(state.sourceRoots)) return

    def ant = projectBuilder.binding.ant

    String targetFolder = projectBuilder.targetFolder
    File dir = new File(targetFolder != null ? targetFolder : ".", "___temp___")
    BuildUtil.deleteDir(projectBuilder, dir.absolutePath)
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
  static class CustomFormInstrumenter extends
          FormInstrumenter {
    final List<File> formFiles;
    final ModuleBuildState state;

    @Override
    void associate(final String formFile, final String classFile) {
    }

    @Override
    void log(String msg, int option) {
      System.out.println(msg);
    }

    @Override
    void fireError(String msg) {
      throw new RuntimeException(msg);
    }

    CustomFormInstrumenter(final File destDir, final List<PrefixedPath> nestedFormPathList, final List<File> ff, final ModuleBuildState s) {
      super(destDir, nestedFormPathList);
      formFiles = ff;
      state = s;
    }
  }

  def JetBrainsInstrumentations(org.jetbrains.jps.ProjectBuilder projectBuilder) {
    projectBuilder.binding.ant.taskdef(name: "jb_instrumentations", classname: "com.intellij.ant.InstrumentIdeaExtensions")
  }

  def getPrefixedPath(org.jetbrains.jps.ProjectBuilder projectBuilder, String root, ModuleChunk moduleChunk) {
    final path = new PrefixedPath(projectBuilder.binding.ant.project, root)

    moduleChunk.elements.each {module ->
      final String prefix = module.sourceRootPrefixes[root]
      if (prefix != null) {
        path.setPrefix(prefix)
      }
    }

    return path
  }

  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, ProjectBuilder projectBuilder) {
    if (state.loader == null) {
      final ArrayList urls = new ArrayList();
      urls.add(new File(state.targetFolder).toURL());
      state.classpath.each {
        urls.add(new File(it).toURL());
      }

      state.loader = new InstrumentationClassFinder((URL[])urls.toArray(new URL[urls.size()]))

      final List<File> formFiles = new ArrayList<File>();
      final List<PrefixedPath> nestedFormDirs = new ArrayList<PrefixedPath>();

      state.sourceRootsFromModuleWithDependencies.each {
        nestedFormDirs << getPrefixedPath(projectBuilder, it, moduleChunk)
      }

      state.formInstrumenter = new CustomFormInstrumenter(new File(state.targetFolder), nestedFormDirs, formFiles, state);

      for (File formFile: formFiles) {
        state.formInstrumenter.instrumentForm(formFile, state.loader);
      }
    }

    if (projectBuilder.useInProcessJavac)
      return;

    new Object() {
      public void traverse(final File root) {
        final File[] files = root.listFiles();

        for (File f: files) {
          final String name = f.getName();

          if (name.endsWith(".class")) {
            InstrumentationUtil.instrumentNotNull(f, state.loader)
          }
          else if (f.isDirectory()) {
            traverse(f)
          }
        }
      }
    }.traverse(new File(state.targetFolder))
    if (state.loader != null) {
      state.loader.releaseResources();
    }
  }
}

class CustomTasksBuilder implements ModuleBuilder {
  List<ModuleBuildTask> tasks = []

  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, ProjectBuilder projectBuilder) {
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
