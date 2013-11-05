/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.debugger.actions.*;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.settings.*;
import com.intellij.debugger.ui.breakpoints.*;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.AbstractDebuggerSession;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import com.intellij.xdebugger.impl.actions.EditBreakpointActionHandler;
import com.intellij.xdebugger.impl.actions.MarkObjectActionHandler;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.settings.DebuggerSettingsPanelProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class JavaDebuggerSupport extends DebuggerSupport {
  private final JavaBreakpointPanelProvider myBreakpointPanelProvider = new JavaBreakpointPanelProvider();
  private final StepOverActionHandler myStepOverActionHandler = new StepOverActionHandler();
  private final StepIntoActionHandler myStepIntoActionHandler = new StepIntoActionHandler();
  private final StepOutActionHandler myStepOutActionHandler = new StepOutActionHandler();
  private final ForceStepOverActionHandler myForceStepOverActionHandler = new ForceStepOverActionHandler();
  private final ForceStepIntoActionHandler myForceStepIntoActionHandler = new ForceStepIntoActionHandler();
  private final RunToCursorActionHandler myRunToCursorActionHandler = new RunToCursorActionHandler();
  private final ForceRunToCursorActionHandler myForceRunToCursorActionHandler = new ForceRunToCursorActionHandler();
  private final ResumeActionHandler myResumeActionHandler = new ResumeActionHandler();
  private final PauseActionHandler myPauseActionHandler = new PauseActionHandler();
  private final ToggleLineBreakpointActionHandler myToggleLineBreakpointActionHandler = new ToggleLineBreakpointActionHandler(false);
  private final ToggleLineBreakpointActionHandler myToggleTemporaryLineBreakpointActionHandler = new ToggleLineBreakpointActionHandler(true);
  private final ShowExecutionPointActionHandler myShowExecutionPointActionHandler = new ShowExecutionPointActionHandler();
  private final EvaluateActionHandler myEvaluateActionHandler = new EvaluateActionHandler();
  private final QuickEvaluateActionHandler myQuickEvaluateHandler = new QuickEvaluateActionHandler();
  private final JavaDebuggerSettingsPanelProvider myDebuggerSettingsPanelProvider = new JavaDebuggerSettingsPanelProvider();
  private final MuteBreakpointsActionHandler myMuteBreakpointsHandler = new MuteBreakpointsActionHandler();
  private final DebuggerActionHandler mySmartStepIntoHandler = new JvmSmartStepIntoActionHandler();
  private final DebuggerActionHandler myAddToWatchedActionHandler = new AddToWatchActionHandler();
  private final JavaMarkObjectActionHandler myMarkObjectActionHandler = new JavaMarkObjectActionHandler();
  private final JavaEditBreakpointActionHandler myEditBreakpointActionHandler = new JavaEditBreakpointActionHandler();

  @NotNull
  public BreakpointPanelProvider<?> getBreakpointPanelProvider() {
    return myBreakpointPanelProvider;
  }

  @NotNull
  public DebuggerActionHandler getStepOverHandler() {
    return myStepOverActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getStepIntoHandler() {
    return myStepIntoActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getSmartStepIntoHandler() {
    return mySmartStepIntoHandler;
  }

  @NotNull
  public DebuggerActionHandler getStepOutHandler() {
    return myStepOutActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getForceStepOverHandler() {
    return myForceStepOverActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getForceStepIntoHandler() {
    return myForceStepIntoActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getRunToCursorHandler() {
    return myRunToCursorActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getForceRunToCursorHandler() {
    return myForceRunToCursorActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getResumeActionHandler() {
    return myResumeActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getPauseHandler() {
    return myPauseActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getToggleLineBreakpointHandler() {
    return myToggleLineBreakpointActionHandler;
  }

  @NotNull
  @Override
  public DebuggerActionHandler getToggleTemporaryLineBreakpointHandler() {
    return myToggleTemporaryLineBreakpointActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getShowExecutionPointHandler() {
    return myShowExecutionPointActionHandler;
  }

  @NotNull
  public DebuggerActionHandler getEvaluateHandler() {
    return myEvaluateActionHandler;
  }

  @NotNull
  public QuickEvaluateHandler getQuickEvaluateHandler() {
    return myQuickEvaluateHandler;
  }

  @NotNull
  @Override
  public DebuggerActionHandler getAddToWatchesActionHandler() {
    return myAddToWatchedActionHandler;
  }

  @NotNull
  public DebuggerToggleActionHandler getMuteBreakpointsHandler() {
    return myMuteBreakpointsHandler;
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
    return myEditBreakpointActionHandler;
  }

  @NotNull
  public DebuggerSettingsPanelProvider getSettingsPanelProvider() {
    return myDebuggerSettingsPanelProvider;
  }

  private static class JavaBreakpointPanelProvider extends BreakpointPanelProvider<Breakpoint> {
    private final List<MyBreakpointManagerListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

    @Override
    public AnAction[] getAddBreakpointActions(@NotNull Project project) {
      List<AnAction> result = new ArrayList<AnAction>();
      BreakpointFactory[] breakpointFactories = BreakpointFactory.getBreakpointFactories();
      for (BreakpointFactory breakpointFactory : breakpointFactories) {
        result.add(new AddJavaBreakpointAction(breakpointFactory));
      }
      return result.toArray(new AnAction[result.size()]);
    }

    @Override
    public void createBreakpointsGroupingRules(Collection<XBreakpointGroupingRule> rules) {
      rules.add(new XBreakpointGroupingByCategoryRule());
      rules.add(new XBreakpointGroupingByPackageRule());
      rules.add(new XBreakpointGroupingByClassRule());
    }

    @Override
    public void addListener(final BreakpointsListener listener, Project project, Disposable disposable) {
      BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
      final MyBreakpointManagerListener listener1 = new MyBreakpointManagerListener(listener, breakpointManager);
      breakpointManager.addBreakpointManagerListener(listener1);
      myListeners.add(listener1);
      Disposer.register(disposable, new Disposable() {
        @Override
        public void dispose() {
          removeListener(listener);
        }
      });
    }

    @Override
    protected void removeListener(BreakpointsListener listener) {
      for (MyBreakpointManagerListener managerListener : myListeners) {
        if (managerListener.myListener == listener) {
          BreakpointManager manager = managerListener.myBreakpointManager;
          manager.removeBreakpointManagerListener(managerListener);
          myListeners.remove(managerListener);
          break;
        }
      }
    }

    public int getPriority() {
      return 1;
    }

    public Breakpoint findBreakpoint(@NotNull final Project project, @NotNull final Document document, final int offset) {
      return DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().findBreakpoint(document, offset, null);
    }

    @Override
    public GutterIconRenderer getBreakpointGutterIconRenderer(Object breakpoint) {
      if (breakpoint instanceof BreakpointWithHighlighter) {
        final RangeHighlighter highlighter = ((BreakpointWithHighlighter)breakpoint).getHighlighter();
        if (highlighter != null) {
          return (GutterIconRenderer)highlighter.getGutterIconRenderer();
        }
      }
      return null;
    }

    public void onDialogClosed(final Project project) {
      DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().updateAllRequests();
    }

    @Override
    public void provideBreakpointItems(Project project, Collection<BreakpointItem> items) {
      for (BreakpointFactory breakpointFactory : BreakpointFactory.getBreakpointFactories()) {
        Key<? extends Breakpoint> category = breakpointFactory.getBreakpointCategory();
        Breakpoint[] breakpoints = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().getBreakpoints(category);
        for (Breakpoint breakpoint : breakpoints) {
          items.add(breakpointFactory.createBreakpointItem(breakpoint));
        }
      }
    }

    private static class AddJavaBreakpointAction extends AnAction {
      private BreakpointFactory myBreakpointFactory;

      public AddJavaBreakpointAction(BreakpointFactory breakpointFactory) {
        myBreakpointFactory = breakpointFactory;
        Presentation p = getTemplatePresentation();
        p.setIcon(myBreakpointFactory.getIcon());
        p.setText(breakpointFactory.getDisplayName());
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setVisible(myBreakpointFactory.canAddBreakpoints());
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        myBreakpointFactory.addBreakpoint(getEventProject(e));
      }
    }

    private static class MyBreakpointManagerListener implements BreakpointManagerListener {

      private final BreakpointsListener myListener;
      public BreakpointManager myBreakpointManager;


      public MyBreakpointManagerListener(BreakpointsListener listener, BreakpointManager breakpointManager) {
        myListener = listener;
        myBreakpointManager = breakpointManager;
      }

      @Override
      public void breakpointsChanged() {
        myListener.breakpointsChanged();
      }
    }
  }

  public static class JavaDebuggerSettingsPanelProvider extends DebuggerSettingsPanelProvider {
    public int getPriority() {
      return 1;
    }

    @Override
    public Configurable getRootConfigurable() {
      return new DebuggerLaunchingConfigurable();
    }

    public Collection<? extends Configurable> getConfigurables() {
      final ArrayList<Configurable> configurables = new ArrayList<Configurable>();
      configurables.add(new DebuggerDataViewsConfigurable(null));
      configurables.add(new DebuggerSteppingConfigurable());
      configurables.add(new UserRenderersConfigurable(null));
      configurables.add(new DebuggerHotswapConfigurable());
      return configurables;
    }

    public void apply() {
      NodeRendererSettings.getInstance().fireRenderersChanged();
    }
  }

  public static Project getCurrentProject() {
    //todo[nik] improve
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    if (project != null) {
      return project;
    }
    return ProjectManager.getInstance().getDefaultProject();
  }
}
