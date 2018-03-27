/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jetbrains.jetCheck.Generator.*;

/**
 * @author peter
 */
public class StatefulGeneratorTest extends PropertyCheckerTestCase {

  public void testShrinkingIntsWithDistributionsDependingOnListSize() {
    Generator<List<InsertChar>> gen = from(data -> {
      AtomicInteger modelLength = new AtomicInteger(0);
      Generator<List<InsertChar>> cmds = listsOf(from(cmdData -> {
        int index = cmdData.drawInt(IntDistribution.uniform(0, modelLength.getAndIncrement()));
        char c = cmdData.generate(asciiLetters());
        return new InsertChar(c, index);
      }));
      return data.generate(cmds);
    });
    List<InsertChar> minCmds = checkGeneratesExample(gen,
                                                     cmds -> InsertChar.performOperations(cmds).contains("ab"),
                                                     64);
    assertEquals(minCmds.toString(), 2, minCmds.size());
  }

  public void testImperativeInsertDeleteCheckCommands() {
    Scenario minHistory = checkFalsified(ImperativeCommand.scenarios(() -> env -> {
      StringBuilder sb = new StringBuilder();
      env.executeCommands(withRecursion(insertStringCmd(sb), deleteStringCmd(sb), checkDoesNotContain(sb, "A")));
    }), Scenario::ensureSuccessful, 42).getMinimalCounterexample().getExampleValue();

    assertEquals("commands:\n" +
                 "  insert A at 0\n" +
                 "  check", 
                 minHistory.toString());
  }

  public void testImperativeInsertReplaceDeleteCommands() {
    Scenario minHistory = checkFalsified(ImperativeCommand.scenarios(() -> env -> {
      StringBuilder sb = new StringBuilder();
      ImperativeCommand replace = env1 -> {
        if (sb.length() == 0) return;
        int index = env1.generateValue(integers(0, sb.length() - 1), null);
        char toReplace = env1.generateValue(asciiLetters().suchThat(c -> c != 'A'), "replace " + sb.charAt(index) + " with %s at " + index);
        sb.setCharAt(index, toReplace);
      };

      env.executeCommands(withRecursion(insertStringCmd(sb), replace, deleteStringCmd(sb), checkDoesNotContain(sb, "A")));
    }), Scenario::ensureSuccessful, 76).getMinimalCounterexample().getExampleValue();

    assertEquals("commands:\n" +
                 "  insert A at 0\n" +
                 "  check",
                 minHistory.toString());
  }

  @NotNull
  private static Generator<ImperativeCommand> withRecursion(ImperativeCommand... commands) {
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
  private static ImperativeCommand insertStringCmd(StringBuilder sb) {
    return env -> {
      int index = env.generateValue(integers(0, sb.length()), null);
      String toInsert = env.generateValue(stringsOf(asciiLetters()), "insert %s at " + index);
      sb.insert(index, toInsert);
    };
  }

  @NotNull
  private static ImperativeCommand deleteStringCmd(StringBuilder sb) {
    return env -> {
      int start = env.generateValue(integers(0, sb.length()), null);
      int end = env.generateValue(integers(start, sb.length()), "deleting (" + start + ", %s)");
      sb.delete(start, end);
    };
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