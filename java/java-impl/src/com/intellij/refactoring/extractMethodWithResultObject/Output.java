// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.hash.HashMap;

import java.util.Map;
import java.util.StringJoiner;

/**
 * @author Pavel.Dolgov
 */
class Output {
  final Map<PsiVariable, Flags> myFlags = new HashMap<>();

  Flags getFlags(PsiVariable variable) {
    return myFlags.get(variable);
  }

  static class Flags {
    boolean isVisibleAtExit;
    boolean isValueUsedAfterExit;
    //boolean isDefinitelyAssignedAtExit;
    //boolean isVisibleAfterExit;

    boolean isUndefined() {
      return !isVisibleAtExit || !isValueUsedAfterExit;
    }

    @Override
    public String toString() {
      StringJoiner joiner = new StringJoiner(",", "{", "}");
      if(isVisibleAtExit) joiner.add("VisibleAtExit");
      if(isValueUsedAfterExit) joiner.add("ValueUsedAfterExit");
      return joiner.toString();
    }
  }
}
