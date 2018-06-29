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
public class SubSequenceTest extends PropertyCheckerTestCase{
  private final String subSequence;
  private final int expectedMinimizations;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public SubSequenceTest(String subSequence, int expectedMinimizations) {
    this.subSequence = subSequence;
    this.expectedMinimizations = expectedMinimizations;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection data() {
    return Arrays.asList(
      new Object[]{"abcde", 399},
      new Object[]{"abcdef", 420},
      new Object[]{"sadf", 107},
      new Object[]{"asdf", 118},
      new Object[]{"xxx", 81},
      new Object[]{"AA", 47}
    );
  }

  @Test
  public void checkGeneratesSubSequence() {
    PropertyFailure.CounterExample<Scenario> example = checkFalsified(Scenario.scenarios(() -> env -> {
      StringBuilder sb = new StringBuilder();
      env.executeCommands(withRecursion(insertStringCmd(sb), deleteStringCmd(sb), e -> {
        if (containsSubSequence(sb.toString(), subSequence)) {
          throw new AssertionError("Found " + sb.toString());
        }
      }));
    }), Scenario::ensureSuccessful, expectedMinimizations).getMinimalCounterexample();
    String log = example.getExampleValue().toString();
    assertEquals(log, "Found " + subSequence, example.getExceptionCause().getMessage());
    assertFalse(log, log.contains(DELETING));
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