package com.intellij.compiler.ant;

import com.intellij.openapi.util.Pair;

import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 25, 2004
 */
public class CompositeGenerator extends Generator{
  private final List<Pair<Generator, Integer>> myGenerators = new ArrayList<Pair<Generator,Integer>>();

  public CompositeGenerator() {
  }

  public CompositeGenerator(Generator generator1, Generator generator2, int emptyLinesCount) {
    add(generator1);
    add(generator2, emptyLinesCount);
  }

  public final void add(Generator generator) {
    add(generator, 0);
  }

  public final void add(Generator generator, int emptyLinesCount) {
    myGenerators.add(new Pair<Generator, Integer>(generator, new Integer(emptyLinesCount)));
  }

  public void generate(DataOutput out) throws IOException {
    for (final Pair<Generator, Integer> myGenerator : myGenerators) {
      final Pair<Generator, Integer> pair = (Pair<Generator, Integer>)myGenerator;
      crlf(out);
      final int emptyLinesCount = pair.getSecond().intValue();
      for (int idx = 0; idx < emptyLinesCount; idx++) {
        crlf(out);
      }
      pair.getFirst().generate(out);
    }
  }

  public final int getGeneratorCount() {
    return myGenerators.size();
  }
}
