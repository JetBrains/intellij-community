package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext;
import com.intellij.tools.build.bazel.jvmIncBuilder.CLFlags;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.BuildContextImpl;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.KotlinCompilerRunner;
import com.intellij.tools.build.bazel.jvmIncBuilder.util.ArgMap;
import com.intellij.tools.build.bazel.jvmIncBuilder.util.ArgMapKt;
import junit.framework.TestCase;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the translation of worker command-line flags into the kotlinc configuration:
 * the CLI options assembled by {@link BuildContextImpl} and the typed {@link K2JVMCompilerArguments}
 * built by {@link KotlinCompilerRunner}.
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

  private static BuildContext createBuildContext(String... args) throws Exception {
    ArgMap<CLFlags> argMap = ArgMapKt.createArgMap(List.of(args), CLFlags.class);
    Map<CLFlags, List<String>> flags = new EnumMap<>(CLFlags.class);
    for (CLFlags flag : CLFlags.values()) {
      List<String> value = argMap.optional(flag);
      if (value != null) {
        flags.put(flag, value);
      }
    }
    Path baseDir = Files.createTempDirectory("build-context-kotlin-options");
    return new BuildContextImpl(baseDir, List.of(), flags, new StringBuilder());
  }

  private static K2JVMCompilerArguments buildKotlinCompilerArguments(BuildContext context) {
    return new KotlinCompilerRunner(context, null).buildKotlinCompilerArguments(context, List.of());
  }
}
