/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool;
import org.jetbrains.jps.builders.java.CannotCreateJavaCompilerException;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.builders.java.JavaSourceTransformer;
import org.jetbrains.jps.incremental.LineOutputWriter;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eugene Zhuravlev
 */
public class JavacMain {
  private static final String JAVA_VERSION = System.getProperty("java.version", "");
  
  //private static final boolean ECLIPSE_COMPILER_SINGLE_THREADED_MODE = Boolean.parseBoolean(System.getProperty("jdt.compiler.useSingleThread", "false"));
  private static final Set<String> FILTERED_OPTIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
    "-d", "-classpath", "-cp", "-bootclasspath"
  )));
  private static final Set<String> FILTERED_SINGLE_OPTIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
    /*javac options*/  "-verbose", "-proc:only", "-implicit:class", "-implicit:none", "-Xprefer:newer", "-Xprefer:source"
  )));
  private static final Set<String> FILE_MANAGER_EARLY_INIT_OPTIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
    "-encoding", "-extdirs", "-endorseddirs", "-processorpath", "-s", "-d", "-h"
  )));

  public static final String JAVA_RUNTIME_VERSION = System.getProperty("java.runtime.version");

  public static boolean compile(Collection<String> options,
                                final Collection<File> sources,
                                Collection<File> classpath,
                                Collection<File> platformClasspath,
                                Collection<File> modulePath,
                                Collection<File> sourcePath,
                                Map<File, Set<File>> outputDirToRoots,
                                final DiagnosticOutputConsumer diagnosticConsumer,
                                final OutputFileConsumer outputSink,
                                CanceledStatus canceledStatus, @NotNull JavaCompilingTool compilingTool) {
    JavaCompiler compiler;
    try {
      compiler = compilingTool.createCompiler();
    }
    catch (CannotCreateJavaCompilerException e) {
      diagnosticConsumer.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, e.getMessage()));
      return false;
    }

    for (File outputDir : outputDirToRoots.keySet()) {
      outputDir.mkdirs();
    }

    final boolean usingJavac = compilingTool instanceof JavacCompilerTool;
    final boolean javacBefore9 = isJavacBefore9(compilingTool);
    final JavacFileManager fileManager = new JavacFileManager(
      new ContextImpl(compiler, diagnosticConsumer, outputSink, canceledStatus, javacBefore9), JavaSourceTransformer.getTransformers()
    );

    if (!platformClasspath.isEmpty()) {
      // for javac6 this will prevent lazy initialization of Paths.bootClassPathRtJar 
      // and thus usage of symbol file for resolution, when this file is not expected to be used
      fileManager.handleOption("-bootclasspath", Collections.singleton("").iterator());
      fileManager.handleOption("-extdirs", Collections.singleton("").iterator()); // this will clear cached stuff
      fileManager.handleOption("-endorseddirs", Collections.singleton("").iterator()); // this will clear cached stuff
    }
    final Collection<String> _options = prepareOptions(options, compilingTool);

    try {
      // to be on the safe side, we'll have to apply all options _before_ calling any of manager's methods
      // i.e. getJavaFileObjectsFromFiles()
      // This way the manager will be properly initialized. Namely, the encoding will be set correctly
      // Note that due to lazy initialization in various components inside javac, handleOption() should be called before setLocation() and others
      // update: for some options their repetitive initialization would be considered as error: e.g. '--patch-module',
      //  therefore we do the trick only for those options that may influence FileManager's state initialization before passing it to getTask() method
      for (Iterator<String> iterator = _options.iterator(); iterator.hasNext(); ) {
        final String option = iterator.next();
        if (FILE_MANAGER_EARLY_INIT_OPTIONS.contains(option)) {
          fileManager.handleOption(option, iterator);
        }
      }

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
          if (!usingJavac && !isOptionSet(options, "-processorpath")) {
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
          fileManager.handleOption("-bootclasspath", Collections.singleton("").iterator()); // this will clear cached stuff
          fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, buildPlatformClasspath(platformClasspath, _options));
        }
        catch (IOException e) {
          fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
          return false;
        }
      }

      if (!modulePath.isEmpty()) {
        final JavaFileManager.Location modulePathLocation = StandardLocation.locationFor("MODULE_PATH");
        if (modulePathLocation != null) { // if this option is supported
          try {
            fileManager.setLocation(modulePathLocation, modulePath);
          }
          catch (IOException e) {
            fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
            return false;
          }
        }
      }

      if (javacBefore9 || !sourcePath.isEmpty() || modulePath.isEmpty()) {
        try {
          // ensure the source path is set;
          // otherwise, if not set, javac attempts to search both classes and sources in classpath;
          // so if some classpath jars contain sources, it will attempt to compile them
          // starting from javac9 it seems that setting empty source path may affect module compilation logic, so starting from javac9
          // we avoid forcing empty sourcepath
          fileManager.setLocation(StandardLocation.SOURCE_PATH, sourcePath);
        }
        catch (IOException e) {
          fileManager.getContext().reportMessage(Diagnostic.Kind.ERROR, e.getMessage());
          return false;
        }
      }

      //noinspection IOResourceOpenedButNotSafelyClosed
      final LineOutputWriter out = new LineOutputWriter() {
        protected void lineAvailable(String line) {
          if (usingJavac) {
            diagnosticConsumer.outputLineAvailable(line);
          }
          else {
            // todo: filter too verbose eclipse output?
          }
        }
      };

      final JavaCompiler.CompilationTask task = compiler.getTask(
        out, wrapWithCallDispatcher(fileManager), diagnosticConsumer, _options, null, fileManager.getJavaFileObjectsFromFiles(sources)
      );
      for (JavaCompilerToolExtension extension : JavaCompilerToolExtension.getExtensions()) {
        try {
          extension.beforeCompileTaskExecution(compilingTool, task, _options, diagnosticConsumer);
        }
        catch (Throwable e) {
          fileManager.getContext().reportMessage(Diagnostic.Kind.MANDATORY_WARNING, extension.getClass() + " : " + e.getMessage());
          e.printStackTrace(System.err);
        }
      }

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
      handleCancelException(diagnosticConsumer);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof CompilationCanceledException) {
        handleCancelException(diagnosticConsumer);
      }
      else {
        throw e;
      }
    }
    finally {
      fileManager.close();
      if (usingJavac) {
        cleanupJavacNameTable();
      }
    }
    return false;
  }

  // methods added to newer versions of StandardJavaFileManager interfaces have default implementations that
  // do not delegate to corresponding methods of FileManager's base implementation
  // this proxy object makes sure the calls, not implemented in our file manager, are dispatched further to the base file manager implementation
  private static StandardJavaFileManager wrapWithCallDispatcher(final JavacFileManager fileManager) {
    //return fileManager;
    return (StandardJavaFileManager)Proxy.newProxyInstance(fileManager.getClass().getClassLoader(), new Class[]{StandardJavaFileManager.class}, new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
          return method.invoke(fileManager.getApiCallHandler(method), args);
        }
        catch (InvocationTargetException e) {
          final Throwable cause = e.getCause();
          throw cause != null? cause : e;
        }
      }
    });
  }

  private static boolean isJavacBefore9(JavaCompilingTool compilingTool) {
    // since java 9 internal API's used by the optimizedFileManager have changed
    return compilingTool instanceof JavacCompilerTool && (JAVA_RUNTIME_VERSION.startsWith("1.8.") || JAVA_RUNTIME_VERSION.startsWith("1.7.") || JAVA_RUNTIME_VERSION.startsWith("1.6."));
  }

  private static void handleCancelException(DiagnosticOutputConsumer diagnosticConsumer) {
    diagnosticConsumer.report(new JpsInfoDiagnostic("Compilation was canceled"));
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

  private static Collection<String> prepareOptions(final Collection<String> options, @NotNull JavaCompilingTool compilingTool) {
    final List<String> result = new ArrayList<String>(compilingTool.getDefaultCompilerOptions());
    boolean skip = false;
    for (String option : options) {
      if (FILTERED_OPTIONS.contains(option)) {
        skip = true;
        continue;
      }
      if (!skip) {
        if (!FILTERED_SINGLE_OPTIONS.contains(option) && !compilingTool.getDefaultCompilerOptions().contains(option)) {
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
        final Class<StandardJavaFileManager> optimizedManagerClass = OptimizedFileManagerUtil.getManagerClass();
        if (optimizedManagerClass != null) {
          try {
            final Constructor<StandardJavaFileManager> constructor = optimizedManagerClass.getConstructor();
            // if optimizedManagerClass is loaded by another classloader, cls.newInstance() will not work
            // that's why we need to call setAccessible() to ensure access
            constructor.setAccessible(true);
            optimizedManager = constructor.newInstance();
            cacheClearMethod = OptimizedFileManagerUtil.getCacheClearMethod();
          }
          catch (Throwable e) {
            if (SystemInfoRt.isWindows) {
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
          message = OptimizedFileManagerUtil.getLoadError();
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
        // the access to static 'freeList' field is synchronized inside javac, so we must use "synchronized" too
        synchronized (freelistField.getDeclaringClass()) { 
          freelistField.set(null, emptyList);
        }
      }
    }
    catch (Throwable ignored) {
    }
  }

  private static class ZipFileIndexCleanupDataHolder {
    @Nullable
    static final Method cacheInstanceGetter;
    @Nullable
    static final Method cacheClearMethod;

    static {
      Method getterMethod = null;
      Method clearMethod = null;
      try {
        //trying JDK 6
        clearMethod = Class.forName("com.sun.tools.javac.zip.ZipFileIndex").getDeclaredMethod("clearCache");
        clearMethod.setAccessible(true);
      }
      catch (Throwable e) {
        try {
          final Class<?> cacheClass = Class.forName("com.sun.tools.javac.file.ZipFileIndexCache");
          clearMethod = cacheClass.getDeclaredMethod("clearCache");
          getterMethod = cacheClass.getDeclaredMethod("getSharedInstance");
          clearMethod.setAccessible(true);
          getterMethod.setAccessible(true);
        }
        catch (Throwable ignored2) {
          clearMethod = null;
          getterMethod = null;
        }
      }

      cacheInstanceGetter = getterMethod;
      cacheClearMethod = clearMethod;
    }
  }

  private static volatile boolean zipCacheCleanupPossible = true;

  public static void clearCompilerZipFileCache() {
    if (zipCacheCleanupPossible) {
      final Method clearMethod = ZipFileIndexCleanupDataHolder.cacheClearMethod;
      if (clearMethod != null) {
        final Method getter = ZipFileIndexCleanupDataHolder.cacheInstanceGetter;
        try {
          Object instance = getter != null? getter.invoke(null) : null;
          clearMethod.invoke(instance);
        }
        catch (Throwable e) {
          zipCacheCleanupPossible = false;
        }
      }
      else {
        zipCacheCleanupPossible = false;
      }
    }
  }


}
