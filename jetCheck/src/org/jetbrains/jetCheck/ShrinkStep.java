/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author peter
 */
abstract class ShrinkStep {

  @Nullable
  abstract StructureNode apply(StructureNode root);

  @Nullable
  abstract ShrinkStep onSuccess(StructureNode smallerRoot);

  @Nullable
  abstract ShrinkStep onFailure();

  @NotNull 
  abstract NodeId getNodeAfter();

  static ShrinkStep create(@NotNull NodeId replaced,
                           @NotNull StructureElement replacement,
                           @Nullable Function<StructureNode, ShrinkStep> onSuccess,
                           @Nullable Supplier<ShrinkStep> onFailure) {
    return new ShrinkStep() {

      @Override
      StructureNode apply(StructureNode root) {
        return root.replace(replaced, replacement);
      }

      @Nullable
      @Override
      ShrinkStep onSuccess(StructureNode smallerRoot) {
        return onSuccess == null ? null : onSuccess.apply(smallerRoot);
      }

      @Nullable
      @Override
      ShrinkStep onFailure() {
        return onFailure == null ? null : onFailure.get();
      }

      @NotNull
      @Override
      NodeId getNodeAfter() {
        return replacement.id;
      }

      @Override
      public String toString() {
        return "replace " + replaced + " with " + replacement;
      }
    };
  }
}

