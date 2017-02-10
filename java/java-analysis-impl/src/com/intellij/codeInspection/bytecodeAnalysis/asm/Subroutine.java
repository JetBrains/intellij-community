/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.tree.JumpInsnNode;
import org.jetbrains.org.objectweb.asm.tree.LabelNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lambdamix
 */
public class Subroutine {

  LabelNode start;
  boolean[] access;
  List<JumpInsnNode> callers;

  private Subroutine() {
  }

  Subroutine(@Nullable final LabelNode start, final int maxLocals,
             @Nullable final JumpInsnNode caller) {
    this.start = start;
    this.access = new boolean[maxLocals];
    this.callers = new ArrayList<>();
    callers.add(caller);
  }

  public Subroutine copy() {
    Subroutine result = new Subroutine();
    result.start = start;
    result.access = new boolean[access.length];
    System.arraycopy(access, 0, result.access, 0, access.length);
    result.callers = new ArrayList<>(callers);
    return result;
  }

  public boolean merge(final Subroutine subroutine) throws AnalyzerException {
    boolean changes = false;
    for (int i = 0; i < access.length; ++i) {
      if (subroutine.access[i] && !access[i]) {
        access[i] = true;
        changes = true;
      }
    }
    if (subroutine.start == start) {
      for (int i = 0; i < subroutine.callers.size(); ++i) {
        JumpInsnNode caller = subroutine.callers.get(i);
        if (!callers.contains(caller)) {
          callers.add(caller);
          changes = true;
        }
      }
    }
    return changes;
  }
}
