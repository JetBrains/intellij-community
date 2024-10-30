// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class MarkedObjectAdditionalContextProvider implements AdditionalContextProvider {
  @Override
  public @NotNull List<AdditionalContextElement> getAdditionalContextElements(@NotNull Project project, @Nullable PsiElement context) {
    XDebugSession xSession = XDebuggerManager.getInstance(project).getCurrentSession();

    XValueMarkers<?, ?> xValueMarkers = null;
    if (xSession instanceof XDebugSessionImpl session) {
      xValueMarkers = session.getValueMarkers();
    }
    else if (ApplicationManager.getApplication().isUnitTestMode()) {
      JavaDebugProcess javaDebugProcess = XDebuggerManager.getInstance(project)
        .getDebugProcesses(JavaDebugProcess.class).stream().findFirst().orElse(null);
      if (javaDebugProcess != null) {
        xValueMarkers = DebuggerUtilsImpl.getValueMarkers(javaDebugProcess.getDebuggerSession().getProcess());
      }
    }
    if (xValueMarkers == null) return Collections.emptyList();
    Map<?, ValueMarkup> markers = xValueMarkers.getAllMarkers();
    if (markers.isEmpty()) return Collections.emptyList();
    return collectMarkedValues(markers);
  }

  private static List<AdditionalContextElement> collectMarkedValues(Map<?, ValueMarkup> markers) {
    final List<AdditionalContextElement> result = new ArrayList<>();
    for (Map.Entry<?, ValueMarkup> entry : markers.entrySet()) {
      ObjectReference objectRef = (ObjectReference)entry.getKey();
      final ValueMarkup markup = entry.getValue();
      String labelName = markup.getText();
      if (!StringUtil.isJavaIdentifier(labelName)) {
        continue;
      }
      try {
        labelName += CodeFragmentFactoryContextWrapper.DEBUG_LABEL_SUFFIX;
        Type type = objectRef.type();
        result.add(new AdditionalContextElement(labelName, type.signature(), type.name(), __ -> objectRef));
      }
      catch (ObjectCollectedException e) {
        //it.remove();
      }
    }
    return result;
  }
}
