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

import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.extractor.Utils;
import com.intellij.psi.codeStyle.extractor.differ.Differ;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Generation {

  private static int GENERATION_POOL_SIZE = 45;
  private static int MUTATION_PER_GEN = 10;
  public  static int GEN_COUNT = 40;
  private List<Gens> myGensPool;
  private int myAge;
  private int myParentKind;

  private Generation(@NotNull final Gens bestGens) {
    myParentKind = -1;
    myGensPool = new ArrayList<>(GENERATION_POOL_SIZE);
    for (int i = 0; i < GENERATION_POOL_SIZE; ++i) {
      // the best goes as is
      myGensPool.add(new Gens(bestGens).mutate(i == 0 ? 0 : MUTATION_PER_GEN));
    }
    myAge = 0;
  }

  private Generation(@NotNull Generation previous, int parentKind) {
    myAge = previous.myAge + 1;
    myParentKind = parentKind;
    myGensPool = new ArrayList<>(GENERATION_POOL_SIZE);
    int mutationsCount = MUTATION_PER_GEN;
    //if (myAge < 30) {
    //  if (myAge < 5) {
    //    mutationsCount = 50;
    //  }
    //  else if (myAge % 5 == 0) {
    //    mutationsCount = 25;
    //  }
    //}
    //else if (myAge < 40) {
    //  mutationsCount = 10;
    //}
    //else if (myAge < 50) {
    //  mutationsCount = 5;
    //}

    final int prevPullSize = previous.myGensPool.size();
    for (int i = 0; i < GENERATION_POOL_SIZE; i++) {
      int parent1 = 0, parent2 = 0, iterations = 0;
      while (parent1 == parent2) {
        parent1 = Utils.getRandomLess(prevPullSize);//~ fitness?
        parent2 = Utils.getRandomLess(prevPullSize);
        if (++iterations > 25) break;
      }
      myGensPool.add(Gens.breed(
        previous.myGensPool.get(parent1),
        previous.myGensPool.get(parent2),
        mutationsCount));
    }
  }

  public static Generation createZeroGeneration(@NotNull Gens gens) {
    return new Generation(gens);
  }

  public static Generation createNextGeneration(Differ differ, @NotNull Generation previous) {
    final int parentKind = previous.reduceToSize(differ, (int)(0.2 * previous.myGensPool.size()));
    return previous.tryAgain() ? new Generation(previous, parentKind) : previous;
  }

  private int reduceToSize(Differ differ, int newPoolSize) {
    List<Pair<Integer, Integer>> ranges = new ArrayList<>(myGensPool.size());

    int i = 0;
    for (final Gens gens : myGensPool) {
      int range = differ.getDifference(gens);
      ranges.add(Pair.create(range, i++));
      if (range == 0) {
        myAge = GEN_COUNT;
        newPoolSize = 1;
        break;
      }
    }

    Collections.sort(ranges, (o1, o2) -> o1.first - o2.first);

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
    return myAge < GEN_COUNT;
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
