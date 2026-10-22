// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.instrumentationJavacPlugin;

import com.google.devtools.build.runfiles.Runfiles;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.instrumentation.ThreadingModelInstrumenter;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.instrumentation.WorkerJavaInstrumentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Compiles small sources with the plugin on the annotation processor path. Some tests compare the class files with the
 * output of the worker's Java instrumenters on the same compile without the plugin. The other tests check the
 * diagnostics and which classes the plugin instruments. They also check the class search in the output of each module.
 */
public final class InstrumentationJavacPluginTest {
  private static final String PLUGIN_JAR_PROPERTY = "instrumentation.javac.plugin.jar";
  private static final List<String> JAVA_BUILDER_COMPILE_POLICY = List.of("-XDcompilePolicy=simple", "--should-stop=ifError=FLOW");

  private static final String SHAPE = """
    package c1;

    import org.jetbrains.annotations.NotNull;

    public abstract class Shape {
      public static final class Circle extends Shape {
        public @NotNull String name(@NotNull String prefix) {
          return prefix + "circle";
        }
      }

      public static final class Square extends Shape {
      }

      public static @NotNull Shape pick(boolean circle, @NotNull Circle c, @NotNull Square s) {
        Shape result;
        if (circle) {
          result = c;
        }
        else {
          result = s;
        }
        return result;
      }

      public Runnable task(@NotNull String value) {
        return new Runnable() {
          @Override
          public void run() {
            System.out.println(value.length());
          }
        };
      }
    }
    """;

  private static final String SAMPLE = """
    package t1;

    import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
    import com.intellij.util.concurrency.annotations.RequiresEdt;
    import org.jetbrains.annotations.NotNull;

    public class Sample {
      @RequiresEdt
      public void paint() {
      }

      @RequiresBackgroundThread
      public void load(@NotNull String path) {
      }
    }
    """;

  private static final String REQUIRES_EDT = """
    package com.intellij.util.concurrency.annotations;
    public @interface RequiresEdt {}
    """;

  private static final String REQUIRES_BACKGROUND_THREAD = """
    package com.intellij.util.concurrency.annotations;
    public @interface RequiresBackgroundThread {}
    """;

  private static final String THREADING_ASSERTIONS = """
    package com.intellij.util.concurrency;
    public final class ThreadingAssertions {
      public static void assertEventDispatchThread() {}
      public static void assertBackgroundThread() {}
    }
    """;

  private static final String OK1 = """
    package e1;

    import org.jetbrains.annotations.NotNull;

    public class Ok1 {
      public String id(@NotNull String s) {
        return s;
      }
    }
    """;

  private static final String MODULE_A = """
    module a {
      requires static org.jetbrains.annotations;
    }
    """;

  private static final String MODULE_B = """
    module b {
    }
    """;

  private static final String PREBUILT = """
    package gen;

    public class Prebuilt {
    }
    """;

  private static final String USER = """
    package p;

    import gen.Prebuilt;
    import org.jetbrains.annotations.NotNull;

    public final class User {
      public static @NotNull Object pick(boolean flag, @NotNull Prebuilt prebuilt, @NotNull String text) {
        Object result;
        if (flag) {
          result = prebuilt;
        }
        else {
          result = text;
        }
        return result;
      }
    }
    """;

  @Rule
  public final TemporaryFolder myTemp = new TemporaryFolder();

  @Test
  public void notNullInstrumentationEqualsTheWorker() throws Exception {
    List<Path> sources = writeSources("c1/Shape.java", SHAPE);
    assertEqualToWorker(sources, List.of(annotationsJar()), Set.of("c1/Shape.class", "c1/Shape$Circle.class"));
  }

  @Test
  public void threadingModelInstrumentationEqualsTheWorker() throws Exception {
    List<Path> classPath = List.of(annotationsJar(), compileThreadingModelLibrary(true));
    List<Path> sources = writeSources("t1/Sample.java", SAMPLE);
    String oldValue = System.setProperty(ThreadingModelInstrumenter.INSTRUMENT_ANNOTATIONS_PROPERTY, "true");
    try {
      Map<String, byte[]> classes = assertEqualToWorker(sources, classPath, Set.of("t1/Sample.class"));
      assertTrue(callsMethod(classes.get("t1/Sample.class"), "assertEventDispatchThread"));
      assertTrue(callsMethod(classes.get("t1/Sample.class"), "assertBackgroundThread"));
    }
    finally {
      restoreProperty(ThreadingModelInstrumenter.INSTRUMENT_ANNOTATIONS_PROPERTY, oldValue);
    }
  }

  @Test
  public void threadingModelInstrumentationWithoutThreadingAssertionsEqualsTheWorker() throws Exception {
    List<Path> classPath = List.of(annotationsJar(), compileThreadingModelLibrary(false));
    List<Path> sources = writeSources("t1/Sample.java", SAMPLE);
    String oldValue = System.setProperty(ThreadingModelInstrumenter.INSTRUMENT_ANNOTATIONS_PROPERTY, "true");
    try {
      Map<String, byte[]> classes = assertEqualToWorker(sources, classPath, Set.of("t1/Sample.class"));
      assertTrue(callsMethod(classes.get("t1/Sample.class"), "assertIsDispatchThread"));
    }
    finally {
      restoreProperty(ThreadingModelInstrumenter.INSTRUMENT_ANNOTATIONS_PROPERTY, oldValue);
    }
  }

  @Test
  public void threadingModelInstrumentationIsOffWithoutTheProperty() throws Exception {
    List<Path> classPath = List.of(annotationsJar(), compileThreadingModelLibrary(true));
    List<Path> sources = writeSources("t1/Sample.java", SAMPLE);
    String oldValue = System.getProperty(ThreadingModelInstrumenter.INSTRUMENT_ANNOTATIONS_PROPERTY);
    System.clearProperty(ThreadingModelInstrumenter.INSTRUMENT_ANNOTATIONS_PROPERTY);
    try {
      Map<String, byte[]> classes = assertEqualToWorker(sources, classPath, Set.of("t1/Sample.class"));
      assertFalse(callsMethod(classes.get("t1/Sample.class"), "assertEventDispatchThread"));
    }
    finally {
      restoreProperty(ThreadingModelInstrumenter.INSTRUMENT_ANNOTATIONS_PROPERTY, oldValue);
    }
  }

  @Test
  public void unreadableClassGetsAWarningAndTheOtherClassesAreInstrumented() throws Exception {
    // javac reports GENERATE for e1.Big and e1.Ok2 after the code size error, but it writes neither class file.
    List<Path> sources = writeSources("e1/Ok1.java", OK1, "e1/Big.java", bigClass(), "e1/Ok2.java", OK1.replace("Ok1", "Ok2"));
    List<String> options = new ArrayList<>(JAVA_BUILDER_COMPILE_POLICY);
    options.add("-nowarn");
    CompileResult result = compile(sources, List.of(annotationsJar()), true, options);
    assertFalse(result.describe(), result.success());
    assertTrue(result.describe(), result.has(Diagnostic.Kind.MANDATORY_WARNING, "Bytecode instrumentation of e1.Big was not possible"));
    assertTrue(result.describe(), result.has(Diagnostic.Kind.MANDATORY_WARNING, "Bytecode instrumentation of e1.Ok2 was not possible"));
    Map<String, byte[]> classes = readClasses(result.output());
    assertEquals(Set.of("e1/Ok1.class"), classes.keySet());
    assertTrue(hasReportNullMethod(classes.get("e1/Ok1.class")));
  }

  @Test
  public void instrumenterFailureIsACompileError() throws Exception {
    // javac compiles m.M against a.A and b.B alone, but the frames of M.local need their supertypes x.X and y.Y.
    Path supertypes = compileLibrary(List.of(), "x/X.java", """
      package x;
      public class X {}
      """, "y/Y.java", """
      package y;
      public class Y {}
      """);
    Path subtypes = compileLibrary(List.of(supertypes), "a/A.java", """
      package a;
      public class A extends x.X {}
      """, "b/B.java", """
      package b;
      public class B extends y.Y {}
      """);
    List<Path> sources = writeSources("m/M.java", """
      package m;

      import org.jetbrains.annotations.NotNull;

      public class M {
        public @NotNull Object local(boolean f, a.A a, b.B b) {
          Object o;
          if (f) {
            o = a;
          }
          else {
            o = b;
          }
          return o;
        }
      }
      """);
    CompileResult result = compile(sources, List.of(annotationsJar(), subtypes), true, List.of());
    assertFalse(result.describe(), result.success());
    assertTrue(result.describe(), result.has(Diagnostic.Kind.ERROR, "Bytecode instrumentation of m.M failed: NotNull Instrumenter"));
    assertFalse(hasReportNullMethod(readClasses(result.output()).get("m/M.class")));
  }

  @Test
  public void fileManagerThatIsNotStandardIsACompileError() throws Exception {
    List<Path> sources = writeSources("e1/Ok1.java", OK1);
    CompileResult result = compile(sources, List.of(annotationsJar()), true, List.of(),
                                   fileManager -> new ForwardingJavaFileManager<>(fileManager) {});
    assertFalse(result.describe(), result.success());
    assertTrue(result.describe(), result.has(Diagnostic.Kind.ERROR, "is not a StandardJavaFileManager"));
  }

  @Test
  public void classInTheOutputOfOneModuleIsFound() throws Exception {
    // An earlier compile leaves gen.Prebuilt in the output of module a. The frames of User.pick need that class, and it
    // has no output request.
    Path output = myTemp.newFolder().toPath();
    CompileResult earlier = compileModules(output, false, "a/module-info.java", MODULE_A, "a/gen/Prebuilt.java", PREBUILT,
                                           "b/module-info.java", MODULE_B);
    assertTrue(earlier.describe(), earlier.success());
    CompileResult result = compileModules(output, true, "a/module-info.java", MODULE_A, "a/p/User.java", USER,
                                          "b/module-info.java", MODULE_B);
    assertTrue(result.describe(), result.success());
    assertTrue(hasReportNullMethod(readClasses(output).get("a/p/User.class")));
  }

  @Test
  public void classInTheOutputOfTwoModulesIsNotFound() throws Exception {
    // gen.Prebuilt is in the output of modules a and b, so the class finder does not know which class javac used.
    Path output = myTemp.newFolder().toPath();
    CompileResult earlier = compileModules(output, false, "a/module-info.java", MODULE_A, "a/gen/Prebuilt.java", PREBUILT,
                                           "b/module-info.java", MODULE_B, "b/gen/Prebuilt.java", PREBUILT);
    assertTrue(earlier.describe(), earlier.success());
    CompileResult result = compileModules(output, true, "a/module-info.java", MODULE_A, "a/p/User.java", USER,
                                          "b/module-info.java", MODULE_B);
    assertFalse(result.describe(), result.success());
    assertTrue(result.describe(), result.has(Diagnostic.Kind.ERROR, "Bytecode instrumentation of p.User failed: NotNull Instrumenter"));
    assertTrue(result.describe(), result.has(Diagnostic.Kind.ERROR, "Class not found: gen.Prebuilt"));
  }

  /**
   * Compiles the sources without the plugin and with it, and runs the worker's instrumenters on the output without the
   * plugin. Both results must be byte-equal, and the plugin must change exactly the expected classes.
   *
   * @return the class files of the compile with the plugin, by their path in the output directory
   */
  private Map<String, byte[]> assertEqualToWorker(List<Path> sources, List<Path> classPath, Set<String> expectedChanged) throws Exception {
    CompileResult plain = compile(sources, classPath, false, List.of());
    assertTrue(plain.describe(), plain.success());
    CompileResult instrumented = compile(sources, classPath, true, List.of());
    assertTrue(instrumented.describe(), instrumented.success());

    Map<String, byte[]> plainClasses = readClasses(plain.output());
    Map<String, byte[]> expected = WorkerJavaInstrumentation.instrument(plainClasses, classPath);
    Map<String, byte[]> actual = readClasses(instrumented.output());
    assertEquals(expected.keySet(), actual.keySet());
    for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
      assertArrayEquals(entry.getKey(), entry.getValue(), actual.get(entry.getKey()));
    }
    Set<String> changed = new TreeSet<>();
    for (Map.Entry<String, byte[]> entry : actual.entrySet()) {
      if (!Arrays.equals(entry.getValue(), plainClasses.get(entry.getKey()))) {
        changed.add(entry.getKey());
      }
    }
    assertEquals(new TreeSet<>(expectedChanged), changed);
    return actual;
  }

  private Path compileThreadingModelLibrary(boolean withThreadingAssertions) throws IOException {
    List<String> pathsAndTexts = new ArrayList<>(List.of(
      "com/intellij/util/concurrency/annotations/RequiresEdt.java", REQUIRES_EDT,
      "com/intellij/util/concurrency/annotations/RequiresBackgroundThread.java", REQUIRES_BACKGROUND_THREAD
    ));
    if (withThreadingAssertions) {
      pathsAndTexts.addAll(List.of("com/intellij/util/concurrency/ThreadingAssertions.java", THREADING_ASSERTIONS));
    }
    return compileLibrary(List.of(), pathsAndTexts.toArray(String[]::new));
  }

  private Path compileLibrary(List<Path> classPath, String... pathsAndTexts) throws IOException {
    CompileResult result = compile(writeSources(pathsAndTexts), classPath, false, List.of());
    assertTrue(result.describe(), result.success());
    return result.output();
  }

  /**
   * Compiles modules in the multi-module mode. Each pair of arguments is a path below the module source path and a
   * text. The annotations jar is on the module path.
   */
  private CompileResult compileModules(Path output, boolean withPlugin, String... pathsAndTexts) throws Exception {
    Path root = myTemp.newFolder().toPath();
    List<Path> sources = writeSources(root, pathsAndTexts);
    List<String> options = List.of("--module-source-path", root.toString(), "--module-path", annotationsJar().toString());
    return compile(sources, List.of(), withPlugin, options, fileManager -> fileManager, output);
  }

  /**
   * Writes the sources into a new directory, in the given order: each pair of arguments is a path and a text.
   */
  private List<Path> writeSources(String... pathsAndTexts) throws IOException {
    return writeSources(myTemp.newFolder().toPath(), pathsAndTexts);
  }

  private static List<Path> writeSources(Path root, String... pathsAndTexts) throws IOException {
    List<Path> files = new ArrayList<>();
    for (int i = 0; i < pathsAndTexts.length; i += 2) {
      Path file = root.resolve(pathsAndTexts[i]);
      Files.createDirectories(file.getParent());
      Files.writeString(file, pathsAndTexts[i + 1]);
      files.add(file);
    }
    return files;
  }

  private CompileResult compile(List<Path> sources,
                                List<Path> classPath,
                                boolean withPlugin,
                                List<String> extraOptions) throws IOException {
    return compile(sources, classPath, withPlugin, extraOptions, fileManager -> fileManager);
  }

  private CompileResult compile(List<Path> sources,
                                List<Path> classPath,
                                boolean withPlugin,
                                List<String> extraOptions,
                                Function<StandardJavaFileManager, JavaFileManager> taskFileManager) throws IOException {
    return compile(sources, classPath, withPlugin, extraOptions, taskFileManager, myTemp.newFolder().toPath());
  }

  /**
   * @param taskFileManager makes the file manager of the compile task from the standard file manager
   * @param output the output directory, which can hold the classes of an earlier compile
   */
  private CompileResult compile(List<Path> sources,
                                List<Path> classPath,
                                boolean withPlugin,
                                List<String> extraOptions,
                                Function<StandardJavaFileManager, JavaFileManager> taskFileManager,
                                Path output) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull("The test needs a JDK compiler", compiler);
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      List<String> options = new ArrayList<>(List.of("-d", output.toString(), "-g", "-proc:none"));
      if (!classPath.isEmpty()) {
        options.add("-classpath");
        options.add(classPath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
      }
      if (withPlugin) {
        // The plugin loads from the processor path, and -XDaccessInternalAPI opens the javac context to that class loader.
        options.addAll(List.of("-processorpath", pluginJar().toString(), "-XDaccessInternalAPI"));
      }
      options.addAll(extraOptions);
      JavaFileManager fileManager = taskFileManager.apply(standardFileManager);
      Boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, standardFileManager.getJavaFileObjectsFromPaths(sources)).call();
      return new CompileResult(Boolean.TRUE.equals(success), output, diagnostics.getDiagnostics());
    }
  }

  private static Path pluginJar() throws IOException {
    String location = System.getProperty(PLUGIN_JAR_PROPERTY);
    assertNotNull("The test needs the system property " + PLUGIN_JAR_PROPERTY, location);
    return Path.of(Runfiles.preload().unmapped().rlocation(location));
  }

  private static Path annotationsJar() throws Exception {
    return Path.of(NotNull.class.getProtectionDomain().getCodeSource().getLocation().toURI());
  }

  private static Map<String, byte[]> readClasses(Path directory) throws IOException {
    Map<String, byte[]> result = new TreeMap<>();
    try (Stream<Path> files = Files.walk(directory)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
        result.put(directory.relativize(file).toString().replace(File.separatorChar, '/'), Files.readAllBytes(file));
      }
    }
    return result;
  }

  private static boolean callsMethod(byte[] classFile, String methodName) {
    boolean[] found = {false};
    new ClassReader(classFile).accept(new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.API_VERSION) {
          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (name.equals(methodName)) {
              found[0] = true;
            }
          }
        };
      }
    }, 0);
    return found[0];
  }

  private static boolean hasReportNullMethod(byte[] classFile) {
    boolean[] found = {false};
    new ClassReader(classFile).accept(new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.startsWith("$$$reportNull$$$")) {
          found[0] = true;
        }
        return null;
      }
    }, 0);
    return found[0];
  }

  /**
   * A class whose static initializer is too large, so javac reports "code too large" and writes no class file for it.
   */
  private static String bigClass() {
    StringBuilder text = new StringBuilder("package e1;\n\npublic class Big {\n  static final int[] DATA = {");
    for (int i = 0; i < 12_000; i++) {
      if (i > 0) {
        text.append(',');
      }
      text.append(i);
    }
    return text.append("};\n}\n").toString();
  }

  private static void restoreProperty(String name, String value) {
    if (value == null) {
      System.clearProperty(name);
    }
    else {
      System.setProperty(name, value);
    }
  }

  private record CompileResult(boolean success, Path output, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    boolean has(Diagnostic.Kind kind, String text) {
      for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
        if (diagnostic.getKind() == kind && diagnostic.getMessage(null).contains(text)) {
          return true;
        }
      }
      return false;
    }

    String describe() {
      return diagnostics.stream()
        .map(diagnostic -> diagnostic.getKind() + ": " + diagnostic.getMessage(null))
        .collect(Collectors.joining(System.lineSeparator()));
    }
  }
}
