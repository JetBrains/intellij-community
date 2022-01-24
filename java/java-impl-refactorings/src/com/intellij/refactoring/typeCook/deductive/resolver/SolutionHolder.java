/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.typeCook.deductive.resolver;

import java.util.LinkedList;

/**
 * @author db
 */
public class SolutionHolder {
  private final LinkedList<Binding> mySolutions = new LinkedList<>();

  public void putSolution(final Binding b1) {
    for (final Binding b2 : mySolutions) {
      switch (b1.compare(b2)) {
        case Binding.WORSE:
        case Binding.SAME:
          return;

        case Binding.BETTER:
          mySolutions.remove(b2);
          mySolutions.addFirst(b1);
          return;

        case Binding.NONCOMPARABLE:
      }
    }

    mySolutions.addFirst(b1);
  }

  public Binding getBestSolution() {
    Binding best = null;
    int width = 0;

    for (final Binding binding : mySolutions) {
      final int w = binding.getWidth();

      if (w > width && binding.isValid()) {
        width = w;
        best = binding;
      }
    }

    return best;
  }
}
