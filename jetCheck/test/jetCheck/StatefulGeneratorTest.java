// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static jetCheck.Generator.*;

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
      ImperativeCommand check = env1 -> {
        env1.logMessage("check");
        if (sb.indexOf("A") >= 0) throw new AssertionError();
      };
      env.executeCommands(recursive(rec -> {
        ImperativeCommand group = env1 -> {
          env1.logMessage("Group");
          env1.executeCommands(rec);
        };
        return frequency(2, constant(group),
                         3, sampledFrom(insertStringCmd(sb), deleteStringCmd(sb), check));
      }));
    }), Scenario::ensureSuccessful, 36).getMinimalCounterexample().getExampleValue();

    assertEquals("commands:\n" +
                 "  Group\n" +
                 "    insert A at 0\n" +
                 "    check", 
                 minHistory.toString());
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