// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.tmhAssertionsKotlinPlugin;

import com.google.devtools.build.runfiles.Runfiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.config.Services;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Compiles small Kotlin sources with the plugin on the plugin classpath. The test compares the class files with the
 * reference instrumentation: the TMHInstrumenter of instrumentation-core on the same compile without the plugin. The
 * comparison of an instrumented class uses the text form of ClassText, because the compiler and the class writer of
 * the reference build the constant pool of an instrumented class in different orders. A class without an instrumented
 * method must keep its bytes.
 */
public final class TmhAssertionsKotlinPluginTest {
  private static final String PLUGIN_JAR_PROPERTY = "tmh.assertions.plugin.jar";
  private static final String CORE_JAR_PROPERTY = "instrumentation.core.jar";
  private static final String ABI_PLUGIN_JAR_PROPERTY = "jvm.abi.gen.plugin.jar";
  private static final String PLUGIN_ID = "com.intellij.tools.build.bazel.tmh-assertions";
  private static final String ENABLED_OPTION = "plugin:" + PLUGIN_ID + ":enabled=";
  private static final String GENERATE_LINE_NUMBERS_OPTION = "plugin:" + PLUGIN_ID + ":generateLineNumbers=";
  private static final String ABI_OUTPUT_OPTION = "plugin:org.jetbrains.kotlin.jvm.abi:outputDir=";

  /**
   * The classes with a method or a constructor that gets a soft assertion.
   */
  private static final Set<String> INSTRUMENTED_CLASSES = Set.of(
    "t1/Sample.class", "t1/Sample$Inner.class", "t1/Painter.class", "t1/Widget.class", "t1/Inliner.class",
    "t1/Drawable.class", "t1/Drawable$DefaultImpls.class", "t1/Canvas.class",
    "t1/Looper$once$1.class", "t1/Loop$go$$inlined$once$1.class"
  );

  /**
   * One class of each jar that a compiler process needs: the compiler, the standard library, the reflection library,
   * the script runtime, the coroutines library and the annotations.
   */
  private static final List<String> COMPILER_CLASSES = List.of(
    "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
    "kotlin.Unit",
    "kotlin.reflect.full.KClasses",
    "kotlin.script.templates.standard.ScriptTemplateWithArgs",
    "kotlinx.coroutines.CoroutineScope",
    "org.jetbrains.annotations.NotNull"
  );

  private static final String ANNOTATIONS = """
    package com.intellij.util.concurrency.annotations

    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.PROPERTY_GETTER)
    annotation class RequiresEdt(val generateAssertion: Boolean = true)

    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.PROPERTY_GETTER)
    annotation class RequiresBackgroundThread(val generateAssertion: Boolean = true)

    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.PROPERTY_GETTER)
    annotation class RequiresReadLock(val generateAssertion: Boolean = true)
    """;

  /**
   * The assertion class records its calls. It has no softAssertReadAccess, so the RequiresReadLock generator is not
   * applicable. The hard assertion methods exist for the check that the Kotlin output does not call them.
   */
  private static final String THREADING_ASSERTIONS = """
    package com.intellij.util.concurrency

    object ThreadingAssertions {
      @JvmField
      val calls: MutableList<String> = ArrayList()

      @JvmStatic
      fun softAssertEventDispatchThread() {
        calls.add("softAssertEventDispatchThread")
      }

      @JvmStatic
      fun softAssertBackgroundThread() {
        calls.add("softAssertBackgroundThread")
      }

      @JvmStatic
      fun assertEventDispatchThread() {
        calls.add("assertEventDispatchThread")
      }

      @JvmStatic
      fun assertBackgroundThread() {
        calls.add("assertBackgroundThread")
      }
    }
    """;

  /**
   * The anonymous object in load gets an EnclosingMethod attribute. The getter of size carries the annotation. Both
   * overloads of greet carry it, because the compiler copies the annotations to the wrapper of a JvmOverloads function.
   * The synthetic method greet$default does not.
   */
  private static final String SAMPLE = """
    package t1

    import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
    import com.intellij.util.concurrency.annotations.RequiresEdt
    import com.intellij.util.concurrency.annotations.RequiresReadLock

    class Sample {
      @RequiresEdt
      fun paint(): String {
        val text = "paint"
        return text + text.length
      }

      @RequiresBackgroundThread
      fun load(path: String): Int {
        val checker = object : Runnable {
          override fun run() {
            check(path.isNotEmpty())
          }
        }
        checker.run()
        return if (path.isEmpty()) -1 else path.length
      }

      @RequiresReadLock
      fun read(): Int = 1

      @RequiresEdt(generateAssertion = false)
      fun quiet() {
      }

      @RequiresEdt
      suspend fun later(): Int = 2

      @get:RequiresEdt
      val size: Int
        get() = 3

      @JvmOverloads
      @RequiresEdt
      fun greet(name: String = "x"): String = "hi " + name

      fun plain(items: List<String>): List<String> = items.map { it.uppercase() }

      class Inner {
        @RequiresEdt
        fun show(count: Int): Int {
          var total = 0
          for (i in 0 until count) {
            total += i
          }
          return total
        }
      }
    }
    """;

  /**
   * Caller inlines the body of the annotated inline function onEdt in the same module. Drawable has an annotated
   * abstract method, which gets no code, and an annotated default method. In the default jvm-default mode of the
   * compiler, Drawable$DefaultImpls gets a static copy of redraw and Canvas gets a bridge for it; the compiler copies
   * the annotation to both, so a call of Canvas.redraw runs the assertion in the bridge and in the body. Printer has an
   * annotated abstract method only, so its class has no instrumented method. Looper has an inline function with an
   * anonymous object that captures the lambda. Loop regenerates the object at its call site from the bytes the compile
   * wrote for it, which carry the assertion call already, so the copy gets no second call.
   */
  private static final String SHAPES = """
    package t1

    import com.intellij.util.concurrency.annotations.RequiresEdt

    enum class Shape { CIRCLE, SQUARE }

    @JvmRecord
    data class Point(val x: Int, val y: Int)

    fun describe(shape: Shape): String = when (shape) {
      Shape.CIRCLE -> "round"
      Shape.SQUARE -> "square"
    }

    object Painter {
      @RequiresEdt
      fun paintAll(shapes: List<Shape>): String = shapes.joinToString { describe(it) }
    }

    class Widget @RequiresEdt constructor(val name: String)

    object Inliner {
      @RequiresEdt
      inline fun <T> onEdt(block: () -> T): T = block()
    }

    class Caller {
      fun call(): Int = Inliner.onEdt { 1 }
    }

    interface Drawable {
      @RequiresEdt
      fun draw(): String

      @RequiresEdt
      fun redraw(): String = draw() + "!"
    }

    class Canvas : Drawable {
      override fun draw(): String = "canvas"
    }

    interface Printer {
      @RequiresEdt
      fun print(): String
    }

    class ConsolePrinter : Printer {
      override fun print(): String = "page"
    }

    object Looper {
      inline fun once(crossinline block: () -> Unit): Runnable = object : Runnable {
        @RequiresEdt
        override fun run() {
          block()
        }
      }
    }

    class Loop {
      fun go(): Int {
        var count = 0
        Looper.once { count++ }.run()
        return count
      }
    }

    class Untouched(val point: Point) {
      fun area(): Int = scale() * point.x * point.y

      inline fun <T> measure(block: () -> T): T = block()

      suspend inline fun <T> later(block: () -> T): T = block()

      private fun scale(): Int = 1
    }
    """;

  /**
   * A caller of the annotated inline function and of the inline function with the annotated object, in another module.
   * It compiles against the ABI output of the module.
   */
  private static final String OTHER = """
    package t2

    import t1.Inliner
    import t1.Looper

    class Other {
      fun go(): Int = Inliner.onEdt { 2 }

      fun loop(): Int {
        var count = 0
        Looper.once { count++ }.run()
        return count
      }
    }
    """;

  /**
   * The assertion class as a Java source of the compile: kotlinc resolves it through the source.
   */
  private static final String THREADING_ASSERTIONS_JAVA = """
    package com.intellij.util.concurrency;

    public final class ThreadingAssertions {
      public static void softAssertEventDispatchThread() {
      }

      public static void softAssertBackgroundThread() {
      }

      public static void assertEventDispatchThread() {
      }
    }
    """;

  @Rule
  public final TemporaryFolder myTemp = new TemporaryFolder();

  @Test
  public void assertionsEqualTheReference() throws Exception {
    Path library = compileLibrary(true);
    List<Path> sources = writeSources("t1/Sample.kt", SAMPLE, "t1/Shapes.kt", SHAPES);
    Map<String, byte[]> classes = assertEqualToReference(sources, library, List.of(library), false);
    byte[] sample = classes.get("t1/Sample.class");
    assertEquals(List.of("softAssertEventDispatchThread"), assertionCalls(sample, "paint"));
    assertEquals(List.of("softAssertBackgroundThread"), assertionCalls(sample, "load"));
    assertEquals(List.of(), assertionCalls(sample, "read"));
    assertEquals(List.of(), assertionCalls(sample, "quiet"));
    assertEquals(List.of(), assertionCalls(sample, "later"));
    assertEquals(List.of("softAssertEventDispatchThread"), assertionCalls(sample, "getSize"));
    assertEquals(List.of("softAssertEventDispatchThread", "softAssertEventDispatchThread"), assertionCalls(sample, "greet"));
    assertEquals(List.of(), assertionCalls(sample, "greet$default"));
    assertEquals(List.of(), assertionCalls(sample, "plain"));
    assertTrue(callsMethod(classes.get("t1/Sample$Inner.class"), "softAssertEventDispatchThread"));
    assertTrue(callsMethod(classes.get("t1/Painter.class"), "softAssertEventDispatchThread"));
    assertTrue(callsMethod(classes.get("t1/Widget.class"), "softAssertEventDispatchThread"));
    assertTrue(callsMethod(classes.get("t1/Inliner.class"), "softAssertEventDispatchThread"));
    byte[] drawable = classes.get("t1/Drawable.class");
    assertEquals(List.of(), assertionCalls(drawable, "draw"));
    assertEquals(List.of("softAssertEventDispatchThread"), assertionCalls(drawable, "redraw"));
    assertEquals(List.of("softAssertEventDispatchThread"), assertionCalls(classes.get("t1/Drawable$DefaultImpls.class"), "redraw"));
    byte[] canvas = classes.get("t1/Canvas.class");
    assertEquals(List.of(), assertionCalls(canvas, "draw"));
    assertEquals(List.of("softAssertEventDispatchThread"), assertionCalls(canvas, "redraw"));
    assertFalse(callsMethod(classes.get("t1/Printer.class"), "softAssertEventDispatchThread"));
    assertFalse(callsMethod(classes.get("t1/ConsolePrinter.class"), "softAssertEventDispatchThread"));
    assertFalse(callsMethod(classes.get("t1/Caller.class"), "softAssertEventDispatchThread"));
    assertEquals(List.of("softAssertEventDispatchThread"), assertionCalls(classes.get("t1/Looper$once$1.class"), "run"));
    assertEquals(List.of("softAssertEventDispatchThread"), assertionCalls(classes.get("t1/Loop$go$$inlined$once$1.class"), "run"));
  }

  /**
   * The ABI output keeps the body of an inline function with its assertion call, and a caller in another module inlines
   * the call. The regenerated copy of the annotated object keeps the one call of its source. The second compile runs
   * without the plugin.
   */
  @Test
  public void callerInAnotherModuleInlinesTheAssertion() throws Exception {
    Path library = compileLibrary(true);
    List<Path> sources = writeSources("t1/Sample.kt", SAMPLE, "t1/Shapes.kt", SHAPES);
    Path abi = compileAbiDirectory(sources, library, List.of(pluginJar(), coreJar(), abiPluginJar()), pluginOptions(true, false));
    CompileResult other = compile(writeSources("t2/Other.kt", OTHER), List.of(library, abi));
    assertTrue(other.describe(), other.success());
    Map<String, byte[]> classes = readClasses(other.output());
    assertEquals(List.of("softAssertEventDispatchThread"), assertionCalls(classes.get("t2/Other.class"), "go"));
    byte[] copy = classes.get("t2/Other$loop$$inlined$once$1.class");
    assertNotNull(classes.keySet().toString(), copy);
    assertEquals(List.of("softAssertEventDispatchThread"), assertionCalls(copy, "run"));
  }

  @Test
  public void lineNumbersEqualTheReference() throws Exception {
    Path library = compileLibrary(true);
    List<Path> sources = writeSources("t1/Sample.kt", SAMPLE, "t1/Shapes.kt", SHAPES);
    assertEqualToReference(sources, library, List.of(library), true);
  }

  /**
   * The JVM verifies the stack map frames of every class of the output, and the instrumented methods call the soft
   * assertions.
   */
  @Test
  public void instrumentedClassesRun() throws Exception {
    Path library = compileLibrary(true);
    List<Path> sources = writeSources("t1/Sample.kt", SAMPLE, "t1/Shapes.kt", SHAPES);
    CompileResult result = compileInstrumented(sources, List.of(library), pluginOptions(true, true));
    assertTrue(result.describe(), result.success());
    URL[] urls = {library.toUri().toURL(), result.output().toUri().toURL()};
    try (URLClassLoader loader = new URLClassLoader(urls, getClass().getClassLoader())) {
      for (String path : readClasses(result.output()).keySet()) {
        Class.forName(path.substring(0, path.length() - ".class".length()).replace('/', '.'), true, loader);
      }
      Class<?> sample = loader.loadClass("t1.Sample");
      Object instance = sample.getConstructor().newInstance();
      assertEquals("paint5", sample.getMethod("paint").invoke(instance));
      assertEquals(3, sample.getMethod("load", String.class).invoke(instance, "abc"));
      assertEquals(1, sample.getMethod("read").invoke(instance));
      sample.getMethod("quiet").invoke(instance);
      assertEquals(3, sample.getMethod("getSize").invoke(instance));
      assertEquals("hi y", sample.getMethod("greet", String.class).invoke(instance, "y"));
      // the wrapper of the JvmOverloads function and the function itself each run their assertion
      assertEquals("hi x", sample.getMethod("greet").invoke(instance));
      Class<?> inner = loader.loadClass("t1.Sample$Inner");
      assertEquals(6, inner.getMethod("show", int.class).invoke(inner.getConstructor().newInstance(), 4));
      Class<?> painter = loader.loadClass("t1.Painter");
      Class<?> shape = loader.loadClass("t1.Shape");
      Object circle = shape.getMethod("valueOf", String.class).invoke(null, "CIRCLE");
      Object painterInstance = painter.getField("INSTANCE").get(null);
      assertEquals("round", painter.getMethod("paintAll", List.class).invoke(painterInstance, List.of(circle)));
      loader.loadClass("t1.Widget").getConstructor(String.class).newInstance("w");
      Class<?> caller = loader.loadClass("t1.Caller");
      assertEquals(1, caller.getMethod("call").invoke(caller.getConstructor().newInstance()));
      Class<?> canvas = loader.loadClass("t1.Canvas");
      assertEquals("canvas!", canvas.getMethod("redraw").invoke(canvas.getConstructor().newInstance()));
      Class<?> printer = loader.loadClass("t1.ConsolePrinter");
      assertEquals("page", printer.getMethod("print").invoke(printer.getConstructor().newInstance()));
      Class<?> loop = loader.loadClass("t1.Loop");
      assertEquals(1, loop.getMethod("go").invoke(loop.getConstructor().newInstance()));
      Class<?> assertions = loader.loadClass("com.intellij.util.concurrency.ThreadingAssertions");
      List<String> expectedCalls = List.of(
        "softAssertEventDispatchThread",  // paint
        "softAssertBackgroundThread",     // load
        "softAssertEventDispatchThread",  // getSize
        "softAssertEventDispatchThread",  // greet(String)
        "softAssertEventDispatchThread",  // greet(): the wrapper
        "softAssertEventDispatchThread",  // greet(): the function
        "softAssertEventDispatchThread",  // Inner.show
        "softAssertEventDispatchThread",  // Painter.paintAll
        "softAssertEventDispatchThread",  // Widget
        "softAssertEventDispatchThread",  // Canvas.redraw: the bridge
        "softAssertEventDispatchThread",  // Canvas.redraw: the body
        "softAssertEventDispatchThread"   // Loop.go: the regenerated object, once
      );
      assertEquals(expectedCalls, assertions.getField("calls").get(null));
    }
  }

  /**
   * The plugin without the option enabled changes nothing, as the worker without its system property.
   */
  @Test
  public void assertionsAreOffWithoutTheOption() throws Exception {
    Path library = compileLibrary(true);
    List<Path> sources = writeSources("t1/Sample.kt", SAMPLE, "t1/Shapes.kt", SHAPES);
    assertEqualToThePlainCompile(sources, library, List.of());
    assertEqualToThePlainCompile(sources, library, pluginOptions(false, false));
  }

  @Test
  public void withoutThreadingAssertionsNothingChanges() throws Exception {
    Path library = compileLibrary(false);
    List<Path> sources = writeSources("t1/Sample.kt", SAMPLE, "t1/Shapes.kt", SHAPES);
    assertEqualToThePlainCompile(sources, library, pluginOptions(true, false));
  }

  /**
   * ThreadingAssertions is a Java source of the compile, as in the platform module that declares it. The compile
   * classpath has no class file of it. The reference instrumentation runs after javac, so it gets the class file from
   * javac.
   */
  @Test
  public void threadingAssertionsAsJavaSourceOfTheCompile() throws Exception {
    Path library = compileLibrary(false);
    List<Path> sources = writeSources(
      "t1/Sample.kt", SAMPLE,
      "t1/Shapes.kt", SHAPES,
      "com/intellij/util/concurrency/ThreadingAssertions.java", THREADING_ASSERTIONS_JAVA
    );
    Path javaClasses = compileJava(sources.get(2));
    Map<String, byte[]> classes = assertEqualToReference(sources, library, List.of(library, javaClasses), false);
    assertTrue(callsMethod(classes.get("t1/Sample.class"), "softAssertEventDispatchThread"));
    assertTrue(callsMethod(classes.get("t1/Widget.class"), "softAssertEventDispatchThread"));
  }

  /**
   * The jvm-abi-gen plugin sees the same declarations and the same methods with the plugin in either order. The ABI
   * output keeps the body of an inline function and the anonymous objects of that body. So the annotated inline function
   * of Inliner and the annotated object of Looper carry their assertion calls in the ABI too, and a caller in another
   * module inlines the calls. Every other class has the ABI of the compile without the plugin.
   */
  @Test
  public void abiOutputDependsOnThePluginForInlineFunctionsOnly() throws Exception {
    Path library = compileLibrary(true);
    List<Path> sources = writeSources("t1/Sample.kt", SAMPLE, "t1/Shapes.kt", SHAPES);
    List<String> options = pluginOptions(true, false);
    Map<String, byte[]> withoutPlugin = compileAbi(sources, library, List.of(abiPluginJar()), List.of());
    Map<String, byte[]> pluginFirst = compileAbi(sources, library, List.of(pluginJar(), coreJar(), abiPluginJar()), options);
    Map<String, byte[]> pluginLast = compileAbi(sources, library, List.of(abiPluginJar(), pluginJar(), coreJar()), options);
    assertEquals(withoutPlugin.keySet(), pluginFirst.keySet());
    assertEquals(withoutPlugin.keySet(), pluginLast.keySet());
    Set<String> withAssertions = new TreeSet<>();
    for (Map.Entry<String, byte[]> entry : withoutPlugin.entrySet()) {
      String name = entry.getKey();
      assertFalse(name, callsMethod(entry.getValue(), "softAssertEventDispatchThread"));
      if (ClassText.of(entry.getValue()).equals(ClassText.of(pluginFirst.get(name)))) {
        assertFalse(name, callsMethod(pluginFirst.get(name), "softAssertEventDispatchThread"));
      }
      else {
        assertTrue(name, callsMethod(pluginFirst.get(name), "softAssertEventDispatchThread"));
        withAssertions.add(name);
      }
      assertArrayEquals(name, pluginFirst.get(name), pluginLast.get(name));
    }
    assertEquals(new TreeSet<>(Set.of("t1/Inliner.class", "t1/Looper$once$1.class")), withAssertions);
  }

  /**
   * The compiler of a build has no instrumentation core on its class path, so the plugin classpath must hold it. The
   * test runs a compiler whose class loader has the compiler jars only. The option reaches the plugin through its own
   * command line processor on the same plugin classpath.
   */
  @Test
  public void pluginClasspathHoldsEveryClassOfThePlugin() throws Exception {
    Path library = compileLibrary(true);
    List<Path> sources = writeSources("t1/Sample.kt", SAMPLE, "t1/Shapes.kt", SHAPES);
    Path output = myTemp.newFolder().toPath();
    List<String> arguments = new ArrayList<>(List.of(
      "-d", output.toString(),
      "-no-stdlib", "-no-reflect",
      "-jvm-target", "17",
      "-classpath", library + File.pathSeparator + kotlinStdlib(),
      "-Xplugin=" + pluginJar(),
      "-Xplugin=" + coreJar(),
      "-P", ENABLED_OPTION + "true"
    ));
    for (Path source : sources) {
      arguments.add(source.toString());
    }
    try (URLClassLoader isolated = new URLClassLoader(compilerJars(), ClassLoader.getPlatformClassLoader())) {
      Class<?> compilerClass = Class.forName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", true, isolated);
      Method exec = compilerClass.getMethod("exec", PrintStream.class, String[].class);
      Object exitCode = exec.invoke(compilerClass.getConstructor().newInstance(), System.err, arguments.toArray(String[]::new));
      assertEquals("OK", exitCode.toString());
    }
    assertTrue(callsMethod(readClasses(output).get("t1/Sample.class"), "softAssertEventDispatchThread"));
  }

  /**
   * Compiles the sources without the plugin and with it, and runs the reference instrumentation on the output without
   * the plugin. Both results must have the same text form. The plugin must change exactly the instrumented classes, and
   * every other class must keep its bytes.
   *
   * @param referenceClassPath the classpath of the class finder of the reference, without the Kotlin standard library
   * @param lineNumbers the plugin option generateLineNumbers, and the same switch of the reference
   * @return the class files of the compile with the plugin, by their path in the output directory
   */
  private Map<String, byte[]> assertEqualToReference(
    List<Path> sources, Path library, List<Path> referenceClassPath, boolean lineNumbers
  ) throws Exception {
    CompileResult plain = compile(sources, List.of(library));
    assertTrue(plain.describe(), plain.success());
    CompileResult instrumented = compileInstrumented(sources, List.of(library), pluginOptions(true, lineNumbers));
    assertTrue(instrumented.describe(), instrumented.success());

    List<Path> finderClassPath = new ArrayList<>(referenceClassPath);
    finderClassPath.add(kotlinStdlib());
    Map<String, byte[]> plainClasses = readClasses(plain.output());
    Map<String, byte[]> expected = ReferenceInstrumentation.instrument(plainClasses, finderClassPath, lineNumbers);
    Map<String, byte[]> actual = readClasses(instrumented.output());
    assertEquals(expected.keySet(), actual.keySet());
    for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
      assertEquals(entry.getKey(), ClassText.of(entry.getValue()), ClassText.of(actual.get(entry.getKey())));
    }
    Set<String> changed = new TreeSet<>();
    for (Map.Entry<String, byte[]> entry : actual.entrySet()) {
      if (!ClassText.of(entry.getValue()).equals(ClassText.of(plainClasses.get(entry.getKey())))) {
        changed.add(entry.getKey());
      }
    }
    assertEquals(new TreeSet<>(INSTRUMENTED_CLASSES), changed);
    for (Map.Entry<String, byte[]> entry : plainClasses.entrySet()) {
      if (!INSTRUMENTED_CLASSES.contains(entry.getKey())) {
        assertArrayEquals(entry.getKey(), entry.getValue(), actual.get(entry.getKey()));
      }
    }
    return actual;
  }

  /**
   * The compile with the plugin must give the same class files as the compile without it, byte for byte.
   */
  private void assertEqualToThePlainCompile(List<Path> sources, Path library, List<String> pluginOptions) throws Exception {
    CompileResult plain = compile(sources, List.of(library));
    assertTrue(plain.describe(), plain.success());
    CompileResult instrumented = compileInstrumented(sources, List.of(library), pluginOptions);
    assertTrue(instrumented.describe(), instrumented.success());
    Map<String, byte[]> plainClasses = readClasses(plain.output());
    Map<String, byte[]> actual = readClasses(instrumented.output());
    assertEquals(plainClasses.keySet(), actual.keySet());
    for (Map.Entry<String, byte[]> entry : plainClasses.entrySet()) {
      assertArrayEquals(entry.getKey(), entry.getValue(), actual.get(entry.getKey()));
    }
  }

  /**
   * @return the ABI class files that jvm-abi-gen writes, by their path in the ABI directory
   */
  private Map<String, byte[]> compileAbi(List<Path> sources, Path library, List<Path> pluginJars, List<String> pluginOptions)
    throws Exception {
    return readClasses(compileAbiDirectory(sources, library, pluginJars, pluginOptions));
  }

  /**
   * @return the ABI directory that jvm-abi-gen writes
   */
  private Path compileAbiDirectory(List<Path> sources, Path library, List<Path> pluginJars, List<String> pluginOptions)
    throws Exception {
    Path abiOutput = myTemp.newFolder().toPath();
    List<String> options = new ArrayList<>(pluginOptions);
    options.add(ABI_OUTPUT_OPTION + abiOutput);
    CompileResult result = compile(sources, List.of(library), pluginJars, options);
    assertTrue(result.describe(), result.success());
    return abiOutput;
  }

  /**
   * Compiles one Java source with the compiler of the JDK.
   *
   * @return the output directory
   */
  private Path compileJava(Path source) throws IOException {
    Path output = myTemp.newFolder().toPath();
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull("The test needs a JDK compiler", compiler);
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      List<String> options = List.of("-d", output.toString());
      Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(List.of(source));
      assertTrue(compiler.getTask(null, fileManager, null, options, null, units).call());
    }
    return output;
  }

  private Path compileLibrary(boolean withThreadingAssertions) throws Exception {
    List<String> pathsAndTexts = new ArrayList<>(List.of("com/intellij/util/concurrency/annotations/Annotations.kt", ANNOTATIONS));
    if (withThreadingAssertions) {
      pathsAndTexts.addAll(List.of("com/intellij/util/concurrency/ThreadingAssertions.kt", THREADING_ASSERTIONS));
    }
    CompileResult result = compile(writeSources(pathsAndTexts.toArray(String[]::new)), List.of());
    assertTrue(result.describe(), result.success());
    return result.output();
  }

  /**
   * Writes the sources into a new directory: each pair of arguments is a path and a text.
   */
  private List<Path> writeSources(String... pathsAndTexts) throws IOException {
    Path root = myTemp.newFolder().toPath();
    List<Path> files = new ArrayList<>();
    for (int i = 0; i < pathsAndTexts.length; i += 2) {
      Path file = root.resolve(pathsAndTexts[i]);
      Files.createDirectories(file.getParent());
      Files.writeString(file, pathsAndTexts[i + 1]);
      files.add(file);
    }
    return files;
  }

  private CompileResult compile(List<Path> sources, List<Path> classPath) throws Exception {
    return compile(sources, classPath, List.of(), List.of());
  }

  /**
   * Compiles the sources with the plugin and the instrumentation core on the plugin classpath, as a build does.
   */
  private CompileResult compileInstrumented(List<Path> sources, List<Path> classPath, List<String> pluginOptions) throws Exception {
    return compile(sources, classPath, List.of(pluginJar(), coreJar()), pluginOptions);
  }

  /**
   * @return the two options of the plugin, as the values of the -P option
   */
  private static List<String> pluginOptions(boolean enabled, boolean lineNumbers) {
    return List.of(ENABLED_OPTION + enabled, GENERATE_LINE_NUMBERS_OPTION + lineNumbers);
  }

  /**
   * Compiles the sources with the compiler of the test classpath, in this process. The Kotlin standard library is on
   * the classpath of the compile.
   *
   * @param pluginJars the plugin classpath, in the order the compiler registers the plugins
   * @param pluginOptions the plugin options, as the values of the -P option
   */
  private CompileResult compile(List<Path> sources, List<Path> classPath, List<Path> pluginJars, List<String> pluginOptions)
    throws Exception {
    Path output = myTemp.newFolder().toPath();
    K2JVMCompilerArguments arguments = new K2JVMCompilerArguments();
    arguments.setDestination(output.toString());
    arguments.setNoStdlib(true);
    arguments.setNoReflect(true);
    arguments.setJvmTarget("17");
    List<Path> fullClassPath = new ArrayList<>(classPath);
    fullClassPath.add(kotlinStdlib());
    arguments.setClasspath(fullClassPath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
    if (!pluginJars.isEmpty()) {
      arguments.setPluginClasspaths(pluginJars.stream().map(Path::toString).toArray(String[]::new));
    }
    if (!pluginOptions.isEmpty()) {
      arguments.setPluginOptions(pluginOptions.toArray(String[]::new));
    }
    List<String> freeArgs = new ArrayList<>();
    for (Path source : sources) {
      freeArgs.add(source.toString());
    }
    arguments.setFreeArgs(freeArgs);
    CollectingMessageCollector messages = new CollectingMessageCollector();
    ExitCode exitCode = new K2JVMCompiler().exec(messages, Services.EMPTY, arguments);
    return new CompileResult(exitCode == ExitCode.OK, output, messages.myMessages);
  }

  private static Path pluginJar() throws IOException {
    return runfile(PLUGIN_JAR_PROPERTY);
  }

  private static Path coreJar() throws IOException {
    return runfile(CORE_JAR_PROPERTY);
  }

  private static Path abiPluginJar() throws IOException {
    return runfile(ABI_PLUGIN_JAR_PROPERTY);
  }

  private static Path runfile(String property) throws IOException {
    String location = System.getProperty(property);
    assertNotNull("The test needs the system property " + property, location);
    return Path.of(Runfiles.preload().unmapped().rlocation(location));
  }

  private static Path kotlinStdlib() throws Exception {
    return Path.of(kotlin.Unit.class.getProtectionDomain().getCodeSource().getLocation().toURI());
  }

  /**
   * @return the jars of the test classpath that hold the compiler classes, each jar once
   */
  private static URL[] compilerJars() throws Exception {
    Set<URI> jars = new LinkedHashSet<>();
    for (String className : COMPILER_CLASSES) {
      jars.add(Class.forName(className).getProtectionDomain().getCodeSource().getLocation().toURI());
    }
    URL[] urls = new URL[jars.size()];
    int i = 0;
    for (URI jar : jars) {
      urls[i++] = jar.toURL();
    }
    return urls;
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

  /**
   * @return the names of the ThreadingAssertions methods that the methods of the given name call, in the order of the
   * methods and of the calls
   */
  private static List<String> assertionCalls(byte[] classFile, String methodName) {
    List<String> calls = new ArrayList<>();
    new ClassReader(classFile).accept(new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (!name.equals(methodName)) {
          return null;
        }
        return new MethodVisitor(Opcodes.API_VERSION) {
          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (owner.equals("com/intellij/util/concurrency/ThreadingAssertions")) {
              calls.add(name);
            }
          }
        };
      }
    }, 0);
    return calls;
  }

  private static final class CollectingMessageCollector implements MessageCollector {
    private final List<String> myMessages = new ArrayList<>();
    private boolean myHasErrors;

    @Override
    public void clear() {
      myMessages.clear();
      myHasErrors = false;
    }

    @Override
    public void report(
      @NotNull CompilerMessageSeverity severity,
      @NotNull String message,
      @Nullable CompilerMessageSourceLocation location
    ) {
      if (severity.isError()) {
        myHasErrors = true;
      }
      if (severity != CompilerMessageSeverity.LOGGING) {
        myMessages.add(severity + ": " + message + (location != null ? " (" + location.getPath() + ":" + location.getLine() + ")" : ""));
      }
    }

    @Override
    public boolean hasErrors() {
      return myHasErrors;
    }
  }

  private record CompileResult(boolean success, Path output, List<String> messages) {
    String describe() {
      return String.join(System.lineSeparator(), messages);
    }
  }
}
