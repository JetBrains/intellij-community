/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.jetbrains.jetCheck.Generator.*;

/**
 * @author peter
 */
public class StatefulGeneratorTest extends PropertyCheckerTestCase {
  static final String DELETING = "deleting";

  public void testShrinkingIntsWithDistributionsDependingOnListSize() {
    Generator<List<InsertChar>> gen = from(data -> {
      AtomicInteger modelLength = new AtomicInteger(0);
      Generator<List<InsertChar>> cmds = listsOf(from(cmdData -> {
        int index = cmdData.generate(integers(0, modelLength.getAndIncrement()));
        char c = cmdData.generate(asciiLetters());
        return new InsertChar(c, index);
      }));
      return data.generate(cmds);
    });
    List<InsertChar> minCmds = checkGeneratesExample(gen,
                                                     cmds -> InsertChar.performOperations(cmds).contains("ab"),
                                                     17);
    assertEquals(minCmds.toString(), 2, minCmds.size());
  }

  public void testImperativeInsertDeleteCheckCommands() {
    Scenario minHistory = checkFalsified(Scenario.scenarios(() -> env -> {
      StringBuilder sb = new StringBuilder();
      env.executeCommands(withRecursion(insertStringCmd(sb), deleteStringCmd(sb), checkDoesNotContain(sb, "A")));
    }), Scenario::ensureSuccessful, 29).getMinimalCounterexample().getExampleValue();

    assertEquals("commands:\n" +
                 "  insert A at 0\n" +
                 "  check", 
                 minHistory.toString());
  }

  public void testImperativeInsertReplaceDeleteCommands() {
    Scenario minHistory = checkFalsified(Scenario.scenarios(() -> env -> {
      StringBuilder sb = new StringBuilder();
      ImperativeCommand replace = env1 -> {
        if (sb.length() == 0) return;
        int index = env1.generateValue(integers(0, sb.length() - 1), null);
        char toReplace = env1.generateValue(asciiLetters().suchThat(c -> c != 'A'), "replace " + sb.charAt(index) + " with %s at " + index);
        sb.setCharAt(index, toReplace);
      };

      env.executeCommands(withRecursion(insertStringCmd(sb), replace, deleteStringCmd(sb), checkDoesNotContain(sb, "A")));
    }), Scenario::ensureSuccessful, 52).getMinimalCounterexample().getExampleValue();

    assertEquals("commands:\n" +
                 "  insert A at 0\n" +
                 "  check",
                 minHistory.toString());
  }

  public void testImperativeCommandRechecking() {
    AtomicInteger counter = new AtomicInteger();
    Supplier<ImperativeCommand> command = () -> env -> {
      List<Integer> list = env.generateValue(listsOf(integers()), "%s");
      if (list.size() > 5 || counter.incrementAndGet() > 50) {
        throw new AssertionError();
      }
    };
    try {
      PropertyChecker.customized().silent().checkScenarios(command);
      fail();
    }
    catch (PropertyFalsified e) {
      assertFalse(e.getMessage(), e.getMessage().contains("forAll(..."));
      assertTrue(e.getMessage(), e.getMessage().contains("rechecking("));
      assertTrue(e.getMessage(), e.getMessage().contains("checkScenarios(..."));

      PropertyFailure<?> failure = e.getFailure();
      try {
        //noinspection deprecation
        PropertyChecker.customized().silent().rechecking(failure.getMinimalCounterexample().getSerializedData()).checkScenarios(command);
        fail();
      }
      catch (PropertyFalsified fromRecheck) {
        assertEquals(e.getBreakingValue(), fromRecheck.getBreakingValue());
      }
    }
  }

  // we shouldn't fail on incomplete data
  // because the test might have failed in the middle of some command execution,
  // and after we fixed the reason of test failure, the command might just want to continue working,
  // but there's no saved data for that
  public void testRecheckingOnIncompleteData() {
    AtomicBoolean shouldFail = new AtomicBoolean(true);
    Supplier<ImperativeCommand> command = () -> env -> {
      for (int i = 0; i < 100; i++) {
        env.generateValue(integers(0, 100), null);
        if (shouldFail.get()) {
          throw new AssertionError();
        }
      }
    };

    try {
      PropertyChecker.customized().silent().checkScenarios(command);
      fail();
    }
    catch (PropertyFalsified e) {
      shouldFail.set(false);

      //noinspection deprecation
      PropertyChecker.customized().silent().rechecking(e.getFailure().getMinimalCounterexample().getSerializedData()).checkScenarios(command);
    }
  }

  @NotNull
  static Generator<ImperativeCommand> withRecursion(ImperativeCommand... commands) {
    return recursive(rec -> {
      ImperativeCommand group = env -> {
        env.logMessage("Group");
        env.executeCommands(rec);
      };
      return frequency(2, constant(group), 3, sampledFrom(commands));
    });
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static ImperativeCommand checkDoesNotContain(StringBuilder sb, String infix) {
    return env -> {
      env.logMessage("check");
      if (sb.indexOf(infix) >= 0) throw new AssertionError();
    };
  }

  @NotNull
  static ImperativeCommand insertStringCmd(StringBuilder sb) {
    return env -> {
      int index = env.generateValue(integers(0, sb.length()), null);
      String toInsert = env.generateValue(stringsOf(asciiLetters()), "insert %s at " + index);
      sb.insert(index, toInsert);
    };
  }

  @NotNull
  static ImperativeCommand deleteStringCmd(StringBuilder sb) {
    return env -> {
      int start = env.generateValue(integers(0, sb.length()), null);
      int end = env.generateValue(integers(start, sb.length()), DELETING + " (" + start + ", %s)");
      sb.delete(start, end);
    };
  }

  private ImperativeCommand heavyCommand() {
    Object[] heavyObject = new Object[100_000];
    heavyObject[42] = new Object();
    return new ImperativeCommand() {
      @Override
      public void performCommand(@NotNull Environment env) {}

      @Override
      public String toString() {
        return super.toString() + Arrays.toString(heavyObject);
      }
    };
  }

  public void testDontFailByOutOfMemoryDueToLeakingObjectsPassedIntoGenerators() {
    PropertyChecker.customized().checkScenarios(() -> env -> 
      env.executeCommands(from(data -> data.generate(sampledFrom(heavyCommand(), heavyCommand(), heavyCommand())))));
  }

}

class InsertChar {
  private final char c;
  private final int index;

  InsertChar(char c, int index) {
    this.c = c;
    this.index = index;
  }

  public void performOperation(StringBuilder sb) {
    sb.insert(index, c);
  }

  @Override
  public String toString() {
    return "insert " + c + " at " + index;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof InsertChar)) return false;
    InsertChar aChar = (InsertChar)o;
    return c == aChar.c && index == aChar.index;
  }

  @Override
  public int hashCode() {
    return Objects.hash(c, index);
  }

  static String performOperations(List<InsertChar> cmds) {
    StringBuilder sb = new StringBuilder();
    cmds.forEach(cmd -> cmd.performOperation(sb));
    return sb.toString();
  }
}