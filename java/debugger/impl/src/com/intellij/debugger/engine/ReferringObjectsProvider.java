// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ReferringObjectsProvider {
  @NotNull
  List<ReferringObject> getReferringObjects(@NotNull EvaluationContextImpl evaluationContext, @NotNull ObjectReference value, long limit)
    throws EvaluateException;

  ReferringObjectsProvider BASIC_JDI = new ReferringObjectsProvider() {
    @NotNull
    @Override
    public List<ReferringObject> getReferringObjects(@NotNull EvaluationContextImpl evaluationContext,
                                                     @NotNull ObjectReference value,
                                                     long limit) {
      return ContainerUtil.map(value.referringObjects(limit), x -> asReferringObject(x, value));
    }

    private ReferringObject asReferringObject(@NotNull ObjectReference referrer, @NotNull ObjectReference referee) {
      Field field = findField(referee, referrer);
      if (field != null) {
        return new FieldReferringObject(referrer, field);
      }

      return new SimpleReferringObject(referrer);
    }

    @Nullable
    private static Field findField(@NotNull Value value, @NotNull ObjectReference reference) {
      for (Field field : reference.referenceType().allFields()) {
        if (reference.getValue(field) == value) {
          return field;
        }
      }

      return null;
    }
  };
}
