// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.Predicate;

public abstract class CompoundPackageSet extends PackageSetBase {
  final PackageSet[] mySets;

  String myText;

  CompoundPackageSet(PackageSet @NotNull ... sets) {
    mySets = sets;
    for (PackageSet set : sets) {
      if (set == null) throw new IllegalArgumentException("null set in " + Arrays.toString(sets));
    }
  }
  
  @Override
  @NotNull
  public PackageSet createCopy() {
    return map(s->s.createCopy());
  }

  @Override
  public boolean anyMatches(@NotNull Predicate<? super PackageSet> predicate) {
    return ContainerUtil.or(mySets, s -> predicate.test(s));
  }

  @Override
  @NotNull
  public String getText() {
    if (myText == null) {
      myText = StringUtil.join(mySets, s->s.getText(), "||");
    }
    return myText;
  }

  public PackageSet @NotNull [] getSets() {
    return mySets;
  }
}