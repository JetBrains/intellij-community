// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel;

import com.intellij.tools.build.bazel.impl.BazelIncBuildTest;
import org.junit.Test;

/**
 * End-to-end proof for MRI-4701 that a per-diagnostic {@code -Xwarning-level} (the {@code x_warning_level}
 * kotlinc option) actually reaches the compiler and changes its behavior.
 * <p>
 * Each scenario compiles a module with {@code warn = "error"} (i.e. {@code -Werror}):
 * <ul>
 *   <li>the initial build is clean and succeeds;</li>
 *   <li>step 1 introduces a real compiler warning into the sources — under {@code -Werror} it becomes an
 *       error and the build turns <b>red</b> ({@code Exit code: ERROR} in build.log);</li>
 *   <li>step 2 adds {@code x_warning_level = ["&lt;DIAGNOSTIC&gt;:warning"]} for exactly that diagnostic — the
 *       warning is downgraded, the same code now compiles and the build turns <b>green</b> ({@code Exit code: OK}).</li>
 * </ul>
 * Verified for two different warning types (DEPRECATION and UNCHECKED_CAST).
 */
public class WarningLevelTests extends BazelIncBuildTest {

  @Test
  public void testWarningLevelSuppressesDeprecationUnderWerror() throws Exception {
    performTest(2, "kotlin/warningLevel/deprecation").assertSuccessful();
  }

  @Test
  public void testWarningLevelSuppressesUncheckedCastUnderWerror() throws Exception {
    performTest(2, "kotlin/warningLevel/uncheckedCast").assertSuccessful();
  }

  /**
   * Uses TWO warning levels at once. The module has both a DEPRECATION and an UNCHECKED_CAST warning
   * under {@code -Werror}:
   * <ul>
   *   <li>step 1: both warnings present, no override -> build red ({@code Exit code: ERROR});</li>
   *   <li>step 2: {@code x_warning_level = ["DEPRECATION:warning"]} -> still red (UNCHECKED_CAST remains);</li>
   *   <li>step 3: {@code x_warning_level = ["DEPRECATION:warning", "UNCHECKED_CAST:warning"]}.</li>
   * </ul>
   * FINDING (MRI-4701 follow-up): step 3 is still <b>red</b>. The worker emits two repeated
   * {@code -Xwarning-level=} flags, and kotlinc does NOT honor them both under {@code -Werror} — the raw
   * output shows both diagnostics remaining warnings and {@code -Werror} escalating the batch:
   * <pre>
   *   Warning: 'fun foo(param: String): Int' is deprecated. message.
   *   Warning: Unchecked cast of 'Any' to 'List&lt;String&gt;'.
   *   Error: warnings found and -Werror specified
   * </pre>
   * A single level works (see the two tests above); two do not. This asserts the INTENDED behavior
   * (both suppressed -> green), so it is a RED repro of the multi-level bug until the worker's
   * -Xwarning-level emission is fixed.
   */
  @Test
  public void testTwoWarningLevelsSuppressBothUnderWerror() throws Exception {
    performTest(3, "kotlin/warningLevel/multiple").assertSuccessful();
  }
}
