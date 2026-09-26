package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext;
import com.intellij.tools.build.bazel.jvmIncBuilder.CLFlags;
import com.intellij.tools.build.bazel.jvmIncBuilder.impl.BuildContextImpl;
import com.intellij.tools.build.bazel.jvmIncBuilder.util.ArgMap;
import com.intellij.tools.build.bazel.jvmIncBuilder.util.ArgMapKt;
import junit.framework.TestCase;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the translation of the worker warning flags into the javac options assembled by
 * {@link BuildContextImpl}. The global {@code --warn} switch maps to {@code -nowarn} or
 * {@code -Werror}. An {@code --x_warning_level} override for a specific warning category
 * suppresses the global javac switch when the override contradicts it, and keeps the switch
 * when the override agrees with it.
 */
public final class BuildContextJavaOptionsTest extends TestCase {

  public void testWarnOffMapsToNowarn() throws Exception {
    assertTrue(javaOptions("--warn", "off").contains("-nowarn"));
  }

  public void testWarnErrorMapsToWerror() throws Exception {
    assertTrue(javaOptions("--warn", "error").contains("-Werror"));
  }

  public void testWarnReportMapsToNoGlobalSwitch() throws Exception {
    List<String> options = javaOptions("--warn", "report");
    assertFalse(options.contains("-nowarn"));
    assertFalse(options.contains("-Werror"));
  }

  public void testRaisedCategorySuppressesNowarn() throws Exception {
    assertFalse(javaOptions("--warn", "off", "--x_warning_level", "DEPRECATION:warning").contains("-nowarn"));
    assertFalse(javaOptions("--warn", "off", "--x_warning_level", "DEPRECATION:error").contains("-nowarn"));
  }

  public void testDisabledCategoryKeepsNowarn() throws Exception {
    assertTrue(javaOptions("--warn", "off", "--x_warning_level", "DEPRECATION:disabled").contains("-nowarn"));
  }

  public void testLoweredCategorySuppressesWerror() throws Exception {
    assertFalse(javaOptions("--warn", "error", "--x_warning_level", "DEPRECATION:warning").contains("-Werror"));
    assertFalse(javaOptions("--warn", "error", "--x_warning_level", "DEPRECATION:disabled").contains("-Werror"));
  }

  public void testErrorCategoryKeepsWerror() throws Exception {
    assertTrue(javaOptions("--warn", "error", "--x_warning_level", "DEPRECATION:error").contains("-Werror"));
  }

  public void testOneContradictingCategoryAmongManySuppressesTheSwitch() throws Exception {
    List<String> offOptions = javaOptions(
      "--warn", "off",
      "--x_warning_level", "DEPRECATION:disabled", "UNCHECKED_CAST:warning"
    );
    assertFalse(offOptions.contains("-nowarn"));

    List<String> errorOptions = javaOptions(
      "--warn", "error",
      "--x_warning_level", "DEPRECATION:error", "UNCHECKED_CAST:disabled"
    );
    assertFalse(errorOptions.contains("-Werror"));
  }

  public void testNoWarnFlagMapsToNoGlobalSwitch() throws Exception {
    List<String> options = javaOptions("--x_warning_level", "DEPRECATION:warning");
    assertFalse(options.contains("-nowarn"));
    assertFalse(options.contains("-Werror"));
  }

  private static List<String> javaOptions(String... warningArgs) throws Exception {
    List<String> args = new ArrayList<>(List.of(
      "--target_label", "//test:lib",
      "--out", "out/lib.jar",
      "--kotlin_module_name", "lib"
    ));
    args.addAll(List.of(warningArgs));
    ArgMap<CLFlags> argMap = ArgMapKt.createArgMap(args, CLFlags.class);
    Map<CLFlags, List<String>> flags = new EnumMap<>(CLFlags.class);
    for (CLFlags flag : CLFlags.values()) {
      List<String> value = argMap.optional(flag);
      if (value != null) {
        flags.put(flag, value);
      }
    }
    BuildContext context = new BuildContextImpl(
      Files.createTempDirectory("build-context-java-options"), List.of(), flags, new StringBuilder()
    );
    return context.getBuilderOptions().getJavaOptions();
  }
}
