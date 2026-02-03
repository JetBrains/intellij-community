// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;

final class ImageObjectRenderer extends AbstractImageRenderer {
  @Override
  protected String getName() {
    return "Image";
  }

  @Override
  protected String getClassName() {
    return "java.awt.Image";
  }

  @Override
  protected FullValueEvaluatorProvider getFullValueEvaluatorProvider() {
    return (evaluationContext, valueDescriptor) ->
      createImagePopupEvaluator(JavaDebuggerBundle.message("message.node.show.image"), evaluationContext,
                                valueDescriptor.getValue(), "imageToBytes");
  }
}
