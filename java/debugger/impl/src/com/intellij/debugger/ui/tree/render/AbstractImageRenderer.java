// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.platform.debugger.impl.shared.ShowImagePopupUtil;
import com.intellij.rt.debugger.ImageSerializer;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

abstract class AbstractImageRenderer extends CompoundRendererProvider {
  private static final Logger LOG = Logger.getInstance(AbstractImageRenderer.class);

  @Override
  public boolean isApplicable(Project project) {
    if (!super.isApplicable(project)) return false;

    var manager = (XDebuggerManagerImpl)XDebuggerManager.getInstance(project);
    return manager.getFrontendCapabilities().getCanShowImages();
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  static byte @Nullable [] getImageBytes(EvaluationContextImpl evaluationContext, Value obj, String methodName) {
    try {
      EvaluationContextImpl copyContext = evaluationContext.createEvaluationContext(obj);
      StringReference bytes =
        (StringReference)DebuggerUtilsImpl.invokeHelperMethod(copyContext, ImageSerializer.class, methodName, Collections.singletonList(obj));
      if (bytes != null) {
        return bytes.value().getBytes(StandardCharsets.ISO_8859_1);
      }
    }
    catch (Exception e) {
      DebuggerUtilsImpl.logError("Exception while getting image data", e);
    }
    return null;
  }

  protected XFullValueEvaluator createImagePopupEvaluator(@NotNull @Nls String message,
                                                          @NotNull EvaluationContextImpl evaluationContext,
                                                          Value imageObj,
                                                          String getBytesMethodName) {
    return new JavaValue.JavaFullValueEvaluator(message, evaluationContext) {
      {
        setShowValuePopup(false);
      }

      @Override
      public void evaluate(@NotNull XFullValueEvaluationCallback callback) {
        if (callback.isObsolete()) return;

        var imageData = getImageBytes(getEvaluationContext(), imageObj, getBytesMethodName);

        ShowImagePopupUtil.showOnFrontend(getEvaluationContext().getProject(), imageData);
        callback.evaluated("");
      }
    };
  }
}
