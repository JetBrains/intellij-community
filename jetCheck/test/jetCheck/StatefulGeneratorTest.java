// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetCheck;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peter
 */
public class StatefulGeneratorTest extends PropertyCheckerTestCase {

  public void testShrinkingIntsWithDistributionsDependingOnListSize() {
    Generator<List<InsertChar>> gen = Generator.from(data -> {
      AtomicInteger modelLength = new AtomicInteger(0);
      Generator<List<InsertChar>> cmds = Generator.listsOf(Generator.from(cmdData -> {
        int index = cmdData.drawInt(IntDistribution.uniform(0, modelLength.getAndIncrement()));
        char c = cmdData.generate(Generator.asciiLetters());
        return new InsertChar(c, index);
      }));
      return data.generate(cmds);
    });
    List<InsertChar> minCmds = checkGeneratesExample(gen,
                                                     cmds -> InsertChar.performOperations(cmds).contains("ab"),
                                                     64);
    assertEquals(minCmds.toString(), 2, minCmds.size());
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