// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.util.List;

public class JvmSmartStepIntoActionHandler extends XSmartStepIntoHandler<JvmSmartStepIntoActionHandler.JvmSmartStepIntoVariant> {
  private final DebuggerSession mySession;

  public JvmSmartStepIntoActionHandler(@NotNull DebuggerSession session) {
    mySession = session;
  }

  @NotNull
  @Override
  public Promise<List<JvmSmartStepIntoVariant>> computeSmartStepVariantsAsync(@NotNull XSourcePosition position) {
    return findVariants(position, true);
  }

  @NotNull
  @Override
  public Promise<List<JvmSmartStepIntoVariant>> computeStepIntoVariants(@NotNull XSourcePosition position) {
    return findVariants(position, false);
  }

  private Promise<List<JvmSmartStepIntoVariant>> findVariants(@NotNull XSourcePosition xPosition, boolean smart) {
    SourcePosition position = DebuggerUtilsEx.toSourcePosition(xPosition, mySession.getProject());
    for (JvmSmartStepIntoHandler handler : JvmSmartStepIntoHandler.EP_NAME.getExtensionList()) {
      if (handler.isAvailable(position)) {
        Promise<List<SmartStepTarget>> targets =
          smart ? handler.findSmartStepTargetsAsync(position, mySession) : handler.findStepIntoTargets(position, mySession);
        return targets.then(results -> ContainerUtil.map(results, target -> new JvmSmartStepIntoVariant(target, handler)));
      }
    }
    return Promises.rejectedPromise();
  }

  @NotNull
  @Override
  public List<JvmSmartStepIntoVariant> computeSmartStepVariants(@NotNull XSourcePosition position) {
    throw new IllegalStateException("Should not be called");
  }

  @Override
  public String getPopupTitle(@NotNull XSourcePosition position) {
    return DebuggerBundle.message("title.smart.step.popup");
  }

  @Override
  public void stepIntoEmpty(XDebugSession session) {
    session.forceStepInto();
  }

  @Override
  public void startStepInto(@NotNull JvmSmartStepIntoVariant variant, @Nullable XSuspendContext context) {
    mySession.stepInto(true, variant.myHandler.createMethodFilter(variant.myTarget));
  }

  static class JvmSmartStepIntoVariant extends XSmartStepIntoVariant {
    private final SmartStepTarget myTarget;
    private final JvmSmartStepIntoHandler myHandler;

    JvmSmartStepIntoVariant(SmartStepTarget target, JvmSmartStepIntoHandler handler) {
      myTarget = target;
      myHandler = handler;
    }

    @Override
    public String getText() {
      return myTarget.getPresentation();
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myTarget.getIcon();
    }

    @Nullable
    @Override
    public TextRange getHighlightRange() {
      PsiElement element = myTarget.getHighlightElement();
      return element != null ? element.getTextRange() : null;
    }
  }
}
