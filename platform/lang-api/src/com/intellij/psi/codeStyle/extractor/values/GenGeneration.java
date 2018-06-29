/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle.extractor.values;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.extractor.Utils;
import com.intellij.psi.codeStyle.extractor.differ.Differ;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.intellij.psi.codeStyle.extractor.Utils.updateState;

public class GenGeneration {
  private static final int GENERATION_POOL_SIZE = 40/*45*/;
  private static final int MUTATION_PER_GEN = 10/*10*/;
  public  static final int GENERATIONS_COUNT = 40/*40*/;
  private List<Gens> myGensPool;
  private int myAge;
  private final int myParentKind;

  private GenGeneration(@NotNull final Gens bestGens) {
    myParentKind = -1;
    myGensPool = new ArrayList<>(GENERATION_POOL_SIZE);
    for (int i = 0; i < GENERATION_POOL_SIZE; ++i) {
      // the best goes as is
      myGensPool.add(new Gens(bestGens).mutate(i == 0 ? 0 : MUTATION_PER_GEN));
    }
    myAge = 0;
  }

  private GenGeneration(@NotNull GenGeneration previous, int parentKind) {
    myAge = previous.myAge + 1;
    myParentKind = parentKind;
    myGensPool = new ArrayList<>(GENERATION_POOL_SIZE);
    int mutationsCount = MUTATION_PER_GEN;

    final int prevPullSize = previous.myGensPool.size();
    for (int i = 0; i < GENERATION_POOL_SIZE; i++) {
      int parent1 = 0, parent2 = 0, iterations = 0;
      while (parent1 == parent2) {
        parent1 = Utils.getRandomLess(prevPullSize);//~ fitness?
        parent2 = Utils.getRandomLess(prevPullSize);
        if (++iterations > (GENERATION_POOL_SIZE/2)) break;
      }
      myGensPool.add(Gens.breed(
        previous.myGensPool.get(parent1),
        previous.myGensPool.get(parent2),
        mutationsCount));
    }
  }

  @NotNull
  public static GenGeneration createZeroGeneration(@NotNull Gens gens) {
    return new GenGeneration(gens);
  }

  public static GenGeneration createNextGeneration(@NotNull Differ differ, @NotNull GenGeneration previous) {
    final int parentKind = previous.reduceToSize(differ, (int)(0.2 * previous.myGensPool.size()));
    return previous.tryAgain() ? new GenGeneration(previous, parentKind) : previous;
  }

  private int reduceToSize(@NotNull Differ differ, int newPoolSize) {
    List<Pair<Integer, Integer>> ranges = new ArrayList<>(myGensPool.size());

    int index = 0;
    for (final Gens gens : myGensPool) {
      int diff = differ.getDifference(gens);
      updateState(ProgressManager.getInstance().getProgressIndicator(),
                  String.format("Generation: %d  Divergence: %d [%d/%d]", myAge, diff, index, myGensPool.size()),
                  false);
      ranges.add(Pair.create(diff, index++));
      if (diff == 0) {
        myAge = GENERATIONS_COUNT;
        newPoolSize = 1;
        break;
      }
    }

    Collections.sort(ranges, Comparator.comparingInt(o -> o.first));

    final ArrayList<Gens> gensPool = new ArrayList<>(newPoolSize);
    int count = 0;
    int worseForward = 0;
    for (final Pair<Integer, Integer> pair : ranges) {
      if (count >= newPoolSize) {
        break;
      }
      Gens gens = myGensPool.get(pair.second);
      gensPool.add(gens);
      ++count;
      worseForward = pair.first;
    }
    myGensPool = gensPool;

    return worseForward;
  }

  public boolean tryAgain() {
    return myAge < GENERATIONS_COUNT;
  }

  public Gens getBestGens(Differ differ) {
    reduceToSize(differ, 1);
    return myGensPool.get(0);
  }

  public int getAge() {
    return myAge;
  }

  public int getParentKind() {
    return myParentKind;
  }
}
