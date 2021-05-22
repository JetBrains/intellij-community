// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui;

import com.intellij.debugger.actions.JavaMarkObjectActionHandler;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.MarkObjectActionHandler;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class JavaDebuggerSupport extends DebuggerSupport {
  private final JavaBreakpointPanelProvider myBreakpointPanelProvider = new JavaBreakpointPanelProvider();
  private final JavaMarkObjectActionHandler myMarkObjectActionHandler = new JavaMarkObjectActionHandler();

  @Override
  @NotNull
  public BreakpointPanelProvider<?> getBreakpointPanelProvider() {
    return myBreakpointPanelProvider;
  }

  @NotNull
  @Override
  public MarkObjectActionHandler getMarkObjectHandler() {
    return myMarkObjectActionHandler;
  }

  private static class JavaBreakpointPanelProvider extends BreakpointPanelProvider<Breakpoint> {
    //private final List<MyBreakpointManagerListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

    @Override
    public void createBreakpointsGroupingRules(Collection<XBreakpointGroupingRule> rules) {
      //rules.add(new XBreakpointGroupingByCategoryRule());
      rules.add(new XBreakpointGroupingByPackageRule());
      rules.add(new XBreakpointGroupingByClassRule());
    }

    @Override
    public void addListener(final BreakpointsListener listener, Project project, Disposable disposable) {
      //BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
      //final MyBreakpointManagerListener listener1 = new MyBreakpointManagerListener(listener, breakpointManager);
      //breakpointManager.addBreakpointManagerListener(listener1);
      //myListeners.add(listener1);
      //Disposer.register(disposable, new Disposable() {
      //  @Override
      //  public void dispose() {
      //    removeListener(listener);
      //  }
      //});
    }

    @Override
    public int getPriority() {
      return 100;
    }

    @Override
    public Breakpoint findBreakpoint(@NotNull final Project project, @NotNull final Document document, final int offset) {
      return null;
      //return DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().findBreakpoint(document, offset, null);
    }

    @Override
    public GutterIconRenderer getBreakpointGutterIconRenderer(Object breakpoint) {
      //if (breakpoint instanceof BreakpointWithHighlighter) {
      //  final RangeHighlighter highlighter = ((BreakpointWithHighlighter)breakpoint).getHighlighter();
      //  if (highlighter != null) {
      //    return (GutterIconRenderer)highlighter.getGutterIconRenderer();
      //  }
      //}
      return null;
    }

    @Override
    public void onDialogClosed(final Project project) {
      //DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().updateAllRequests();
    }

    @Override
    public void provideBreakpointItems(Project project, Collection<BreakpointItem> items) {
      //for (BreakpointFactory breakpointFactory : BreakpointFactory.getBreakpointFactories()) {
      //  Key<? extends Breakpoint> category = breakpointFactory.getBreakpointCategory();
      //  Breakpoint[] breakpoints = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().getBreakpoints(category);
      //  for (Breakpoint breakpoint : breakpoints) {
      //    items.add(breakpointFactory.createBreakpointItem(breakpoint));
      //  }
      //}
    }
  }

  /** @deprecated This method is an unreliable hack, find another way to locate a project instance. */
  @Deprecated
  public static Project getContextProjectForEditorFieldsInDebuggerConfigurables() {
    //todo[nik] improve
    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    if (frame != null) {
      Project project = frame.getProject();
      if (project != null) {
        return project;
      }
    }
    return ProjectManager.getInstance().getDefaultProject();
  }
}
