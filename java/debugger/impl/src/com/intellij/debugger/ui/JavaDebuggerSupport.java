/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.actions.JavaMarkObjectActionHandler;
import com.intellij.debugger.actions.JvmSmartStepIntoActionHandler;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.xdebugger.AbstractDebuggerSession;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import com.intellij.xdebugger.impl.actions.EditBreakpointActionHandler;
import com.intellij.xdebugger.impl.actions.MarkObjectActionHandler;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * @author nik
 */
public class JavaDebuggerSupport extends DebuggerSupport {
  private final JavaBreakpointPanelProvider myBreakpointPanelProvider = new JavaBreakpointPanelProvider();
  //private final QuickEvaluateActionHandler myQuickEvaluateHandler = new QuickEvaluateActionHandler();
  private final DebuggerActionHandler mySmartStepIntoHandler = new JvmSmartStepIntoActionHandler();
  private final JavaMarkObjectActionHandler myMarkObjectActionHandler = new JavaMarkObjectActionHandler();

  @Override
  @NotNull
  public BreakpointPanelProvider<?> getBreakpointPanelProvider() {
    return myBreakpointPanelProvider;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getStepOverHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getStepIntoHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getSmartStepIntoHandler() {
    return mySmartStepIntoHandler;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getStepOutHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getForceStepOverHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getForceStepIntoHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getRunToCursorHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getForceRunToCursorHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getResumeActionHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getPauseHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getToggleLineBreakpointHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  @Override
  public DebuggerActionHandler getToggleTemporaryLineBreakpointHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getShowExecutionPointHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @Override
  @NotNull
  public DebuggerActionHandler getEvaluateHandler() {
    return DisabledActionHandler.INSTANCE;
  }

  @NotNull
  @Override
  public DebuggerActionHandler getAddToWatchesActionHandler() {
    return DisabledActionHandler.INSTANCE;
  }


  private static final DebuggerToggleActionHandler DISABLED_TOGGLE_HANDLER = new DebuggerToggleActionHandler() {
    @Override
    public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
      return false;
    }

    @Override
    public boolean isSelected(@NotNull Project project, AnActionEvent event) {
      return false;
    }

    @Override
    public void setSelected(@NotNull Project project, AnActionEvent event, boolean state) {
    }
  };

  @Override
  @NotNull
  public DebuggerToggleActionHandler getMuteBreakpointsHandler() {
    return DISABLED_TOGGLE_HANDLER;
  }

  @NotNull
  @Override
  public MarkObjectActionHandler getMarkObjectHandler() {
    return myMarkObjectActionHandler;
  }

  @Override
  public AbstractDebuggerSession getCurrentSession(@NotNull Project project) {
    final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    return context != null ? context.getDebuggerSession() : null;
  }

  @NotNull
  @Override
  public EditBreakpointActionHandler getEditBreakpointAction() {
    return X_EDIT;
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
    protected void removeListener(BreakpointsListener listener) {
      //for (MyBreakpointManagerListener managerListener : myListeners) {
      //  if (managerListener.myListener == listener) {
      //    BreakpointManager manager = managerListener.myBreakpointManager;
      //    manager.removeBreakpointManagerListener(managerListener);
      //    myListeners.remove(managerListener);
      //    break;
      //  }
      //}
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

    //private static class AddJavaBreakpointAction extends AnAction {
    //  private BreakpointFactory myBreakpointFactory;
    //
    //  public AddJavaBreakpointAction(BreakpointFactory breakpointFactory) {
    //    myBreakpointFactory = breakpointFactory;
    //    Presentation p = getTemplatePresentation();
    //    p.setIcon(myBreakpointFactory.getIcon());
    //    p.setText(breakpointFactory.getDisplayName());
    //  }
    //
    //  @Override
    //  public void update(AnActionEvent e) {
    //    e.getPresentation().setVisible(myBreakpointFactory.canAddBreakpoints());
    //  }
    //
    //  @Override
    //  public void actionPerformed(AnActionEvent e) {
    //    myBreakpointFactory.addBreakpoint(getEventProject(e));
    //  }
    //}

    //private static class MyBreakpointManagerListener implements BreakpointManagerListener {
    //
    //  private final BreakpointsListener myListener;
    //  public BreakpointManager myBreakpointManager;
    //
    //
    //  public MyBreakpointManagerListener(BreakpointsListener listener, BreakpointManager breakpointManager) {
    //    myListener = listener;
    //    myBreakpointManager = breakpointManager;
    //  }
    //
    //  @Override
    //  public void breakpointsChanged() {
    //    myListener.breakpointsChanged();
    //  }
    //}
  }

  public static Project getContextProjectForEditorFieldsInDebuggerConfigurables() {
    //todo[nik] improve
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    if (project != null) {
      return project;
    }
    return ProjectManager.getInstance().getDefaultProject();
  }

  private static final EditBreakpointActionHandler X_EDIT = new EditBreakpointActionHandler() {
    @Override
    protected void doShowPopup(Project project, JComponent component, Point whereToShow, Object breakpoint) {
      DebuggerUIUtil.showXBreakpointEditorBalloon(project, whereToShow, component, false, (XBreakpoint)breakpoint);
    }

    @Override
    public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
      return false;
    }
  };
}
