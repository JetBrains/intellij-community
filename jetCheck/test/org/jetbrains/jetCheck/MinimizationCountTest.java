// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jetCheck;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.jetbrains.jetCheck.StatefulGeneratorTest.*;

/**
 * @author peter
 */
@RunWith(Parameterized.class)
public class MinimizationCountTest extends PropertyCheckerTestCase{
  private final String subSequence;
  private final int expectedMinimizations;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public MinimizationCountTest(String subSequence, int expectedMinimizations) {
    this.subSequence = subSequence;
    this.expectedMinimizations = expectedMinimizations;
  }

  @Parameterized.Parameters(name = "subSequence={0}")
  public static Collection data() {
    return Arrays.asList(
      new Object[]{"abcde", 392}, 
      new Object[]{"abcdef", 411},
      new Object[]{"sadf", 116},
      new Object[]{"asdf", 132},
      new Object[]{"xxx", 96},
      new Object[]{"AA", 60}
    );
  }

  @Test
  public void checkGeneratesSubSequence() {
    Throwable cause = checkFalsified(ImperativeCommand.scenarios(() -> env -> {
      StringBuilder sb = new StringBuilder();
      env.executeCommands(withRecursion(insertStringCmd(sb), deleteStringCmd(sb), e -> {
        if (containsSubSequence(sb.toString(), subSequence)) {
          throw new AssertionError("Found " + sb.toString());
        }
      }));
    }), Scenario::ensureSuccessful, expectedMinimizations).getMinimalCounterexample().getExceptionCause();
    assertEquals("Found " + subSequence, cause.getMessage());
  }


  private static boolean containsSubSequence(String string, String subSequence) {
    int pos = -1;
    for (int i = 0; i < subSequence.length(); i++) {
      pos = string.indexOf(subSequence.charAt(i), pos + 1);
      if (pos < 0) return false;
    }
    return true;
  }

}