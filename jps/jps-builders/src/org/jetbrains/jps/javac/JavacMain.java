/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.javac;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.java.JavaSourceTransformer;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.LineOutputWriter;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/21/12
 */
public class JavacMain {
  private static final boolean IS_VM_6_VERSION = System.getProperty("java.version", "1.6").contains("1.6");
  //private static final boolean ECLIPSE_COMPILER_SINGLE_THREADED_MODE = Boolean.parseBoolean(System.getProperty("jdt.compiler.useSingleThread", "false"));
  private static final Set<String> FILTERED_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    "-d", "-classpath", "-cp", "-bootclasspath"
  ));
  private static final Set<String> FILTERED_SINGLE_OPTIONS = new HashSet<String>(Arrays.<String>asList(
    /*javac options*/  "-verbose", "-proc:only", "-implicit:class", "-implicit:none", "-Xprefer:newer", "-Xprefer:source",
    /*eclipse options*/"-noExit"
  ));

  public static boolean compile(Collection<String> options,
                                final Collection<File> sources,
                                Collection<File> classpath,
                                Collection<File> platformClasspath,
                                Collection<File> sourcePath,
                                Map<File, Set<File>> outputDirToRoots,
                                final DiagnosticOutputConsumer diagnosticConsumer,
                                final OutputFileConsumer outputSink,
                                CanceledStatus canceledStatus, boolean useEclipseCompiler) {
    JavaCompiler compiler = null;
    if (useEclipseCompiler) {
      for (JavaCompiler javaCompiler : ServiceLoader.load(JavaCompiler.class)) {
        compiler = javaCompiler;
        break;
      }
      if (compiler == null) {
        diagnosticConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, "Eclipse Batch Compiler was not found in classpath"));
        return false;
      }
    }

    final boolean nowUsingJavac;
    if (compiler == null) {
      compiler = ToolProvider.getSystemJavaCompiler();
      if (compiler == null) {
        String message = "System Java Compiler was not found in classpath";
        // trying to obtain additional diagnostic for the case when compiler.jar is present, but there were problems with compiler class loading:
        try {
          Class.forName("com.sun.tools.javac.api.JavacTool", false, JavacMain.class.getClassLoader());
        }
        catch (Throwable ex) {
          message = message + ":\n" + ExceptionUtil.getThrowableText(ex);
        }
        diagnosticConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, message));
        return false;
      }
      nowUsingJavac = true;
    }
    else {
      nowUsingJavac = false;
    }

    for (File outputDir : outputDirToRoots.keySet()) {
      outputDir.mkdirs();
    }
    
    final List<JavaSourceTransformer> transformers = getSourceTransformers();

    final JavacFileManager fileManager = new JavacFileManager(new ContextImpl(compiler, diagnosticConsumer, outputSink, canceledStatus, nowUsingJavac), transformers);

    fileManager.handleOption("-bootclasspath", Collections.singleton("").iterator()); // this will clear cached stuff
    fileManager.handleOption("-extdirs", Collections.singleton("").iterator()); // this will clear cached stuff
    fileManager.handleOption("-endorseddirs", Collections.singleton("").iterator()); // this will clear cached stuff
    final Collection<String> _options = prepareOptions(options, nowUsingJavac);

    try {
      fileManager.setOutputDirectories(outputDirToRoots);
    }
    catch (IOException e) {
      fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
      return false;
    }

    if (!classpath.isEmpty()) {
      try {
        fileManager.setLocation(StandardLocation.CLASS_PATH, classpath);
        if (!nowUsingJavac && !isOptionSet(options, "-processorpath")) {
          // for non-javac file manager ensure annotation processor path defaults to classpath
          fileManager.setLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH, classpath);
        }
      }
      catch (IOException e) {
        fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
        return false;
      }
    }
    if (!platformClasspath.isEmpty()) {
      try {
        fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, buildPlatformClasspath(platformClasspath, _options));
      }
      catch (IOException e) {
        fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
        return false;
      }
    }
    try {
    // ensure the source path is set;
    // otherwise, if not set, javac attempts to search both classes and sources in classpath;
    // so if some classpath jars contain sources, it will attempt to compile them
      fileManager.setLocation(StandardLocation.SOURCE_PATH, sourcePath);
    }
    catch (IOException e) {
      fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
      return false;
    }

    //noinspection IOResourceOpenedButNotSafelyClosed
    final LineOutputWriter out = new LineOutputWriter() {
      protected void lineAvailable(String line) {
        if (nowUsingJavac) {
          diagnosticConsumer.outputLineAvailable(line);
        }
        else {
          // todo: filter too verbose eclipse output?
        }
      }
    };

    try {

      // to be on the safe side, we'll have to apply all options _before_ calling any of manager's methods
      // i.e. getJavaFileObjectsFromFiles()
      // This way the manager will be properly initialized. Namely, the encoding will be set correctly
      for (Iterator<String> iterator = _options.iterator(); iterator.hasNext(); ) {
        fileManager.handleOption(iterator.next(), iterator);
      }

      final JavaCompiler.CompilationTask task = compiler.getTask(
        out, fileManager, diagnosticConsumer, _options, null, fileManager.getJavaFileObjectsFromFiles(sources)
      );

      //if (!IS_VM_6_VERSION) { //todo!
      //  // Do not add the processor for JDK 1.6 because of the bugs in javac
      //  // The processor's presence may lead to NPE and resolve bugs in compiler
      //  final JavacASTAnalyser analyzer = new JavacASTAnalyser(outConsumer, !annotationProcessingEnabled);
      //  task.setProcessors(Collections.singleton(analyzer));
      //}
      return task.call();
    }
    catch(IllegalArgumentException e) {
      diagnosticConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, e.getMessage()));
    }
    catch (CompilationCanceledException ignored) {
      diagnosticConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.OTHER, "Compilation was canceled"));
    }
    finally {
      fileManager.close();
      if (nowUsingJavac) {
        cleanupJavacNameTable();
      }
    }
    return false;
  }

  private static List<JavaSourceTransformer> getSourceTransformers() {
    final Class<JavaSourceTransformer> transformerClass = JavaSourceTransformer.class;
    final ServiceLoader<JavaSourceTransformer> loader = ServiceLoader.load(transformerClass, transformerClass.getClassLoader());
    final List<JavaSourceTransformer> transformers = new SmartList<JavaSourceTransformer>();
    for (JavaSourceTransformer t : loader) {
      transformers.add(t);
    }
    return transformers;
  }

  private static boolean isAnnotationProcessingEnabled(final Collection<String> options) {
    for (String option : options) {
      if ("-proc:none".equals(option)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isOptionSet(final Collection<String> options, String option) {
    for (String opt : options) {
      if (option.equals(opt)) {
        return true;
      }
    }
    return false;
  }

  private static Collection<String> prepareOptions(final Collection<String> options, boolean usingJavac) {
    final List<String> result = new ArrayList<String>();
    if (usingJavac) {
      result.add("-implicit:class"); // the option supported by javac only
    }
    else { // is Eclipse
      result.add("-noExit");
    }
    boolean skip = false;
    for (String option : options) {
      if (FILTERED_OPTIONS.contains(option)) {
        skip = true;
        continue;
      }
      if (!skip) {
        if (!FILTERED_SINGLE_OPTIONS.contains(option)) {
          result.add(option);
        }
      }
      skip = false;
    }
    return result;
  }

  private static Collection<File> buildPlatformClasspath(Collection<File> platformClasspath, Collection<String> options) {
    final Map<PathOption, String> argsMap = new HashMap<PathOption, String>();
    for (Iterator<String> iterator = options.iterator(); iterator.hasNext(); ) {
      final String arg = iterator.next();
      for (PathOption pathOption : PathOption.values()) {
        if (pathOption.parse(argsMap, arg, iterator)) {
          break;
        }
      }
    }
    if (argsMap.isEmpty()) {
      return platformClasspath;
    }
    
    final List<File> result = new ArrayList<File>();
    appendFiles(argsMap, PathOption.PREPEND_CP, result, false);
    appendFiles(argsMap, PathOption.ENDORSED, result, true);
    appendFiles(argsMap, PathOption.D_ENDORSED, result, true);
    result.addAll(platformClasspath);
    appendFiles(argsMap, PathOption.APPEND_CP, result, false);
    appendFiles(argsMap, PathOption.EXTDIRS, result, true);
    appendFiles(argsMap, PathOption.D_EXTDIRS, result, true);
    return result;
  }

  private static void appendFiles(Map<PathOption, String> args, PathOption option, Collection<File> container, boolean listDir) {
    final String path = args.get(option);
    if (path == null) {
      return;
    }
    final StringTokenizer tokenizer = new StringTokenizer(path, File.pathSeparator, false);
    while (tokenizer.hasMoreTokens()) {
      final File file = new File(tokenizer.nextToken());
      if (listDir) {
        final File[] files = file.listFiles();
        if (files != null) {
          for (File f : files) {
            final String fName = f.getName();
            if (fName.endsWith(".jar") || fName.endsWith(".zip")) {
              container.add(f);
            }
          }
        }
      }
      else {
        container.add(file);
      }
    }
  }

  enum PathOption {
    PREPEND_CP("-Xbootclasspath/p:"), 
    ENDORSED("-endorseddirs"), D_ENDORSED("-Djava.endorsed.dirs="),
    APPEND_CP("-Xbootclasspath/a:"),
    EXTDIRS("-extdirs"), D_EXTDIRS("-Djava.ext.dirs=");

    private final String myArgName;
    private final boolean myIsSuffix;

    PathOption(String name) {
      myArgName = name;
      myIsSuffix = name.endsWith("=") || name.endsWith(":");
    }

    public boolean parse(Map<PathOption, String> container, String arg, Iterator<String> rest) {
      if (myIsSuffix) {
        if (arg.startsWith(myArgName)) {
          container.put(this, arg.substring(myArgName.length()));
          return true;
        }
      }
      else {
        if (arg.equals(myArgName)) {
          if (rest.hasNext()) {
            container.put(this, rest.next());
          }
          return true;
        }
      }
      return false;
    }
  }

  private static class ContextImpl implements JavacFileManager.Context {
    private final StandardJavaFileManager myStdManager;
    @Nullable
    private final Method myCacheClearMethod;
    private final DiagnosticOutputConsumer myOutConsumer;
    private final OutputFileConsumer myOutputFileSink;
    private final CanceledStatus myCanceledStatus;
    private static final AtomicBoolean ourOptimizedManagerMissingReported = new AtomicBoolean(false);

    public ContextImpl(@NotNull JavaCompiler compiler,
                       @NotNull DiagnosticOutputConsumer outConsumer,
                       @NotNull OutputFileConsumer sink,
                       CanceledStatus canceledStatus, boolean canUseOptimizedmanager) {
      myOutConsumer = outConsumer;
      myOutputFileSink = sink;
      myCanceledStatus = canceledStatus;
      StandardJavaFileManager optimizedManager = null;
      Method cacheClearMethod = null;
      if (canUseOptimizedmanager) {
        final Class<StandardJavaFileManager> optimizedManagerClass = ClasspathBootstrap.getOptimizedFileManagerClass();
        if (optimizedManagerClass != null) {
          try {
            final Constructor<StandardJavaFileManager> constructor = optimizedManagerClass.getConstructor();
            // if optimizedManagerClass is loaded by another classloader, cls.newInstance() will not work
            // that's why we need to call setAccessible() to ensure access
            constructor.setAccessible(true); 
            optimizedManager = constructor.newInstance();
            cacheClearMethod = ClasspathBootstrap.getOptimizedFileManagerCacheClearMethod();
          }
          catch (Throwable e) {
            if (SystemInfo.isWindows) {
              reportMissingOptimizedManager(outConsumer, e.getMessage());
            }
          }
        }
        else {
          reportMissingOptimizedManager(outConsumer, null);
        }
      }
      myCacheClearMethod = cacheClearMethod;
      if (optimizedManager != null) {
        myStdManager = optimizedManager;
      }
      else {
        myStdManager = compiler.getStandardFileManager(outConsumer, Locale.US, null);
      }
    }

    private static void reportMissingOptimizedManager(DiagnosticOutputConsumer outConsumer, String message) {
      if (!ourOptimizedManagerMissingReported.getAndSet(true)) {
        if (message == null) {
          message = ClasspathBootstrap.getOptimizedFileManagerLoadError();
          if (message == null) {
            message = "";
          }
        }
        outConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.OTHER, "JPS build failed to load optimized file manager for javac:\n" + message));
      }
    }

    public boolean isCanceled() {
      return myCanceledStatus.isCanceled();
    }

    public StandardJavaFileManager getStandardFileManager() {
      return myStdManager;
    }

    public void reportMessage(final Diagnostic.Kind kind, String message) {
      myOutConsumer.report(new PlainMessageDiagnostic(kind, message));
    }

    public void consumeOutputFile(@NotNull final OutputFileObject cls) {
      try {
        myOutputFileSink.save(cls);
      }
      finally {
        final Method cacheClearMethod = myCacheClearMethod;
        if (cacheClearMethod != null) {
          try {
            cacheClearMethod.invoke(myStdManager, cls.getFile());
          }
          catch (Throwable e) {
            //noinspection UseOfSystemOutOrSystemErr
            e.printStackTrace(System.err);
          }
        }
      }
    }
  }

  private static final class NameTableCleanupDataHolder {
    static final Object emptyList;
    static final Field freelistField;

    static {
      try {
        final ClassLoader loader = ToolProvider.getSystemToolClassLoader();
        if (loader == null) {
          throw new RuntimeException("no tools provided");
        }

        final Class<?> listClass = Class.forName("com.sun.tools.javac.util.List", true, loader);
        final Method nilMethod = listClass.getDeclaredMethod("nil");
        emptyList = nilMethod.invoke(null);

        Field freelistRef;
        try {
          // trying jdk 6
          freelistRef = Class.forName("com.sun.tools.javac.util.Name$Table", true, loader).getDeclaredField("freelist");
        }
        catch (Exception e) {
          // trying jdk 7
          freelistRef = Class.forName("com.sun.tools.javac.util.SharedNameTable", true, loader).getDeclaredField("freelist");
        }
        freelistRef.setAccessible(true);
        freelistField = freelistRef;
      }
      catch(RuntimeException e) {
        throw e;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static void cleanupJavacNameTable() {
    try {
      final Field freelistField = NameTableCleanupDataHolder.freelistField;
      final Object emptyList = NameTableCleanupDataHolder.emptyList;
        // both parameters should be non-null if properly initialized
      if (freelistField != null && emptyList != null) {
        freelistField.set(null, emptyList);
      }
    }
    catch (Throwable ignored) {
    }
  }

}
