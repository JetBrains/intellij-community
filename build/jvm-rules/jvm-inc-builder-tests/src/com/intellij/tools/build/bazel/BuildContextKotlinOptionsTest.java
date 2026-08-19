package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext;
import com.intellij.tools.build.bazel.jvmIncBuilder.CLFlags;
import com.intellij.tools.build.bazel.jvmIncBuilder.ExitCode;
import com.intellij.tools.build.bazel.jvmIncBuilder.StorageManager;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.BuildContextImpl;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.KotlinCompilerRunner;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.OutputSinkImpl;
import com.intellij.tools.build.bazel.jvmIncBuilder.runner.OutputSink;
import com.intellij.tools.build.bazel.jvmIncBuilder.util.ArgMap;
import com.intellij.tools.build.bazel.jvmIncBuilder.util.ArgMapKt;
import com.intellij.tools.build.bazel.orderedplugins.OrderedTestPluginFirst;
import com.intellij.tools.build.bazel.orderedplugins.OrderedTestPluginSecond;
import com.intellij.tools.build.bazel.orderedplugins.OrderedTestPluginThird;
import com.intellij.tools.build.bazel.orderedplugins.PluginInvocationRecorder;
import junit.framework.TestCase;
import kotlin.Unit;
import org.jetbrains.bazel.jvm.Input;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Checks the translation of worker command-line flags into the kotlinc configuration:
 * the CLI options assembled by {@link BuildContextImpl} and the typed {@link K2JVMCompilerArguments}
 * built by {@link KotlinCompilerRunner}; also checks that the {@code --x_compiler_plugin_order}
 * constraints actually reorder the compiler-plugin invocations in a real in-process compilation.
 */
public final class BuildContextKotlinOptionsTest extends TestCase {

  public void testCompilerPluginOrderConstraintsPassedToCompilerArguments() throws Exception {
    BuildContext context = createBuildContext(
      "--target_label", "//test:lib",
      "--out", "out/lib.jar",
      "--kotlin_module_name", "lib",
      "--x_compiler_plugin_order", "org.example.first>org.example.second", "org.example.second>org.example.third"
    );

    // the option must not be passed in the CLI form: KotlinCompilerRunner parses the CLI options
    // with overrideArguments=true, which keeps only the last occurrence of a repeatable option
    for (String option : context.getBuilderOptions().getKotlinOptions()) {
      assertFalse(option, option.startsWith("-Xcompiler-plugin-order"));
    }

    K2JVMCompilerArguments arguments = buildKotlinCompilerArguments(context);
    assertEquals(
      List.of("org.example.first>org.example.second", "org.example.second>org.example.third"),
      List.of(arguments.getPluginOrderConstraints())
    );
  }

  public void testNoCompilerPluginOrderConstraintsWhenFlagAbsent() throws Exception {
    BuildContext context = createBuildContext(
      "--target_label", "//test:lib",
      "--out", "out/lib.jar",
      "--kotlin_module_name", "lib"
    );
    K2JVMCompilerArguments arguments = buildKotlinCompilerArguments(context);
    assertEquals(0, arguments.getPluginOrderConstraints().length);
  }

  public void testCompilerPluginsCalledInConfiguredOrder() throws Exception {
    CompileResult result = compileWithPluginOrderConstraints("tests.order.first>tests.order.second", "tests.order.second>tests.order.third");
    result.assertSuccessful();
    assertEquals(List.of("tests.order.first", "tests.order.second", "tests.order.third"), result.pluginInvocations());
  }

  public void testCompilerPluginsCalledInReversedConfiguredOrder() throws Exception {
    CompileResult result = compileWithPluginOrderConstraints("tests.order.third>tests.order.second", "tests.order.second>tests.order.first");
    result.assertSuccessful();
    assertEquals(List.of("tests.order.third", "tests.order.second", "tests.order.first"), result.pluginInvocations());
  }

  public void testCompilerPluginOrderConstraintCycleFailsCompilationBeforePluginsRun() throws Exception {
    CompileResult result = compileWithPluginOrderConstraints("tests.order.first>tests.order.second", "tests.order.second>tests.order.first");
    assertEquals("Compiler messages:\n" + result.messages(), ExitCode.ERROR, result.exitCode());
    assertTrue(result.messages(), result.messages().contains("is part of an constraint cycle"));
    assertEquals("plugins must not be invoked when order constraints are cyclic", List.of(), result.pluginInvocations());
  }

  private record CompileResult(ExitCode exitCode, String messages, boolean hasErrors, List<String> pluginInvocations) {
    void assertSuccessful() {
      assertEquals("Compiler messages:\n" + messages, ExitCode.OK, exitCode);
      assertFalse("Compiler messages:\n" + messages, hasErrors);
    }
  }

  /**
   * Runs a real in-process kotlinc compilation through {@link KotlinCompilerRunner#compile} with three
   * test compiler plugins registered via {@code --plugin_id}/{@code --plugin_classpath} and the given
   * {@code --x_compiler_plugin_order} constraints, and reports the exit code, the compiler messages and
   * the plugin ids in the order the plugins were called. The plugins are registered in an order
   * ([second, third, first]) that matches neither of the constraint sets used by the tests, so the
   * expected results can only be produced by the constraint-driven reordering.
   */
  private static CompileResult compileWithPluginOrderConstraints(String... constraints) throws Exception {
    Path baseDir = Files.createTempDirectory("build-context-kotlin-plugin-order");

    Files.createDirectories(baseDir.resolve("src"));
    Files.writeString(baseDir.resolve("src/Hello.kt"), "class Hello\n");

    // --plugin_classpath entries are ':'-separated worker-level values resolved against the base dir,
    // so the jars are addressed by base-dir-relative paths
    writePluginJar(baseDir.resolve("plugins/second.jar"), OrderedTestPluginSecond.class);
    writePluginJar(baseDir.resolve("plugins/third.jar"), OrderedTestPluginThird.class);
    writePluginJar(baseDir.resolve("plugins/first.jar"), OrderedTestPluginFirst.class);

    // the worker always passes -no-stdlib, so put the jar containing the Kotlin runtime on the classpath;
    // copied under the base dir because BuildContextImpl relativizes every --cp entry against it,
    // which fails on Windows when the temp dir and the jar live on different drives
    Path stdlibJar = Path.of(
      Objects.requireNonNull(Unit.class.getProtectionDomain().getCodeSource(), "cannot locate the jar containing kotlin.Unit")
        .getLocation().toURI()
    );
    Files.createDirectories(baseDir.resolve("libs"));
    Files.copy(stdlibJar, baseDir.resolve("libs/kotlin-stdlib.jar"));

    List<String> args = new ArrayList<>(List.of(
      "--target_label", "//test:plugin-order",
      "--out", "out/lib.jar",
      "--kotlin_module_name", "plugin-order",
      "--srcs", "src/Hello.kt",
      "--cp", "libs/kotlin-stdlib.jar",
      "--plugin_id", "tests.order.second", "tests.order.third", "tests.order.first",
      "--plugin_classpath", "plugins/second.jar", "plugins/third.jar", "plugins/first.jar",
      "--x_compiler_plugin_order"
    ));
    args.addAll(List.of(constraints));

    // BuildContextImpl requires an input digest for every --srcs and --cp entry (keyed by the exact flag value);
    // the digest bytes themselves are not validated in a one-shot compilation
    List<Input> inputs = List.of(
      new Input("src/Hello.kt", new byte[]{1}),
      new Input("libs/kotlin-stdlib.jar", new byte[]{1}),
      new Input("plugins/first.jar", new byte[]{1}),
      new Input("plugins/second.jar", new byte[]{1}),
      new Input("plugins/third.jar", new byte[]{1})
    );

    StringBuilder messages = new StringBuilder();
    BuildContext context = createBuildContext(baseDir, inputs, messages, args.toArray(String[]::new));
    StorageManager storageManager = new StorageManager(context);
    try {
      KotlinCompilerRunner runner = new KotlinCompilerRunner(context, storageManager);
      OutputSink outputSink = new OutputSinkImpl(storageManager);
      PluginInvocationRecorder.reset();
      ExitCode exitCode = runner.compile(context.getSources().getElements(), List.of(), context, outputSink);
      return new CompileResult(exitCode, messages.toString(), context.hasErrors(), PluginInvocationRecorder.getInvocations());
    }
    finally {
      storageManager.close(false);
    }
  }

  /**
   * Packs a single-registrar compiler plugin jar: the registrar class file and the META-INF/services entry
   * that the worker's plugin loading reads. {@link PluginInvocationRecorder} is deliberately not packed:
   * the worker's plugin classloader is parent-first, so the registrar resolves the recorder from the test
   * classpath and shares its static state with the test.
   */
  private static void writePluginJar(Path jarPath, Class<?> registrarClass) throws IOException {
    Files.createDirectories(jarPath.getParent());
    String classEntry = registrarClass.getName().replace('.', '/') + ".class";
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
      jar.putNextEntry(new ZipEntry(classEntry));
      try (InputStream classBytes = registrarClass.getClassLoader().getResourceAsStream(classEntry)) {
        Objects.requireNonNull(classBytes, classEntry).transferTo(jar);
      }
      jar.closeEntry();
      jar.putNextEntry(new ZipEntry("META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar"));
      jar.write(registrarClass.getName().getBytes(StandardCharsets.UTF_8));
      jar.closeEntry();
    }
  }

  private static BuildContext createBuildContext(String... args) throws Exception {
    return createBuildContext(Files.createTempDirectory("build-context-kotlin-options"), List.of(), new StringBuilder(), args);
  }

  private static BuildContext createBuildContext(Path baseDir, Iterable<Input> inputs, StringBuilder messageSink, String... args) {
    ArgMap<CLFlags> argMap = ArgMapKt.createArgMap(List.of(args), CLFlags.class);
    Map<CLFlags, List<String>> flags = new EnumMap<>(CLFlags.class);
    for (CLFlags flag : CLFlags.values()) {
      List<String> value = argMap.optional(flag);
      if (value != null) {
        flags.put(flag, value);
      }
    }
    return new BuildContextImpl(baseDir, inputs, flags, messageSink);
  }

  private static K2JVMCompilerArguments buildKotlinCompilerArguments(BuildContext context) {
    return new KotlinCompilerRunner(context, null).buildKotlinCompilerArguments(context, List.of());
  }
}
