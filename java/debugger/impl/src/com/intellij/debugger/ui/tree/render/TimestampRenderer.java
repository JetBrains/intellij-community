// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.sun.jdi.LongType;
import com.sun.jdi.LongValue;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;

public class TimestampRenderer implements NodeRendererProvider {
  @Override
  public @NotNull NodeRenderer createRenderer() {
    return new RendererBuilder("Timestamp")
      .labelRenderer(new LabelRenderer() {
        @Override
        public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener) {
          Value value = descriptor.getValue();
          if (value == null) {
            return "null";
          }
          else if (value instanceof LongValue) {
            return new Timestamp(((LongValue)value).longValue()).toString();
          }
          return null;
        }
      })
      .isApplicable(type -> CompletableFuture.completedFuture(type instanceof LongType))
      .build();
  }
}
