package org.jetbrains.jps.builders

import com.intellij.ant.InstrumentationUtil
import com.intellij.ant.InstrumentationUtil.FormInstrumenter
import com.intellij.ant.PrefixedPath
import org.apache.tools.ant.BuildListener
import org.jetbrains.ether.ProjectWrapper
import org.jetbrains.ether.dependencyView.AntListener
import org.jetbrains.ether.dependencyView.StringCache
import org.jetbrains.ether.dependencyView.StringCache.S
import org.jetbrains.jps.builders.javacApi.Java16ApiCompilerRunner
import org.jetbrains.jps.*

/**
 * @author max
 */
class JavacBuilder implements ModuleBuilder, ModuleCycleBuilder {

    def preprocessModuleCycle(ModuleBuildState state, ModuleChunk moduleChunk, org.jetbrains.jps.Project project) {
        doBuildModule(moduleChunk, state)
    }

    def processModule(ModuleBuildState state, ModuleChunk moduleChunk, org.jetbrains.jps.Project project) {
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

    def processModule(ModuleBuildState state, ModuleChunk moduleChunk, org.jetbrains.jps.Project project) {
        if (state.iterated) return;

        state.iterated = true

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
    def GroovycBuilder(org.jetbrains.jps.Project project) {
        project.taskdef(name: "groovyc", classname: "org.codehaus.groovy.ant.Groovyc")
    }

    def processModule(ModuleBuildState state, ModuleChunk moduleChunk, org.jetbrains.jps.Project project) {
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

    def GroovyStubGenerator(org.jetbrains.jps.Project project) {
        project.taskdef(name: "generatestubs", classname: "org.codehaus.groovy.ant.GenerateStubsTask")
    }

    def processModule(ModuleBuildState state, ModuleChunk moduleChunk, org.jetbrains.jps.Project project) {
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
    class CustomFormInstrumenter extends
            FormInstrumenter {
        final List<String> formFiles;
        final ModuleBuildState state;

        @Override
        void associate(final String formFile, final String classFile) {
            if (state.callback != null) {
                final String formRelPath = state.projectWrapper.getRelativePath(formFile);
                final String classRelPath = state.projectWrapper.getRelativePath(classFile);

                state.callback.associateForm(StringCache.get(formRelPath), StringCache.get(classRelPath));
            }
        }

        @Override
        void log(String msg, int option) {
            System.out.println(msg);
        }

        @Override
        void fireError(String msg) {
            throw new RuntimeException(msg);
        }

        CustomFormInstrumenter(final File destDir, final List<String> nestedFormPathList, final List<String> ff, final ModuleBuildState s) {
            super(destDir, nestedFormPathList);
            formFiles = ff;
            state = s;
        }
    }

    def JetBrainsInstrumentations(org.jetbrains.jps.Project project) {
        project.taskdef(name: "jb_instrumentations", classname: "com.intellij.ant.InstrumentIdeaExtensions")
    }

    def getPrefixedPath(org.jetbrains.jps.Project project, String root, ModuleChunk moduleChunk) {
        final path = new PrefixedPath(project.binding.ant.project, root)

        moduleChunk.elements.each {module ->
            final String prefix = module.sourceRootPrefixes[root]
            if (prefix != null) {
                path.setPrefix(prefix)
            }
        }

        return path
    }

    def processModule(ModuleBuildState state, ModuleChunk moduleChunk, org.jetbrains.jps.Project project) {
        if (state.loader == null) {
            final StringBuffer cp = new StringBuffer()

            cp.append(state.targetFolder)
            cp.append(File.pathSeparator)

            state.classpath.each {
                cp.append(it)
                cp.append(File.pathSeparator)
            }

            state.loader = InstrumentationUtil.createPseudoClassLoader(cp.toString())

            final List<File> formFiles = new ArrayList<File>();
            final ProjectWrapper pw = state.projectWrapper;

            if (pw != null) {
              for (Module m: moduleChunk.elements) {
                  final Set<S> names = state.tests ? pw.getModule(m.getName()).getTests() : pw.getModule(m.getName()).getSources();
                  for (S name: names) {
                      if (name.value.endsWith(".form")) {
                          formFiles.add(new File(pw.getAbsolutePath(name.value)));
                      }
                  }
              }
            }

            final List<PrefixedPath> nestedFormDirs = new ArrayList<PrefixedPath>();

            state.sourceRoots.each {
                nestedFormDirs << getPrefixedPath(project, it, moduleChunk)
            }

            state.moduleDependenciesSourceRoots.each {
                nestedFormDirs << getPrefixedPath(project, it, moduleChunk)
            }

            state.formInstrumenter = new CustomFormInstrumenter(new File(state.targetFolder), nestedFormDirs, formFiles, state);

            if (!state.incremental) {
                for (File formFile: formFiles) {
                    state.formInstrumenter.instrumentForm(formFile, state.loader);
                }
            }
        }

        if (state.incremental) {
            for (String f: state.sourceFiles) {
                if (f.endsWith(".form")) {
                    state.formInstrumenter.instrumentForm(new File(f), state.loader);
                }
            }
        }

        if (project.getBuilder().useInProcessJavac)
            return;

        if (!state.incremental) {
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
        }
        else {
            final Collection<StringCache.S> classes = state.callback.getClassFiles()

            classes.each {
                InstrumentationUtil.instrumentNotNull(new File(state.targetFolder + File.separator + it.value + ".class"), state.loader)
            }
        }
    }
}

class CustomTasksBuilder implements ModuleBuilder {
    List<ModuleBuildTask> tasks = []

    def processModule(ModuleBuildState state, ModuleChunk moduleChunk, org.jetbrains.jps.Project project) {
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