/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointFactory;
import com.intellij.debugger.ui.breakpoints.BreakpointPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.AbstractDebuggerSession;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import com.intellij.xdebugger.impl.breakpoints.ui.AbstractBreakpointPanel;
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
  private final ToggleLineBreakpointActionHandler myToggleLineBreakpointActionHandler = new ToggleLineBreakpointActionHandler();
  private final ShowExecutionPointActionHandler myShowExecutionPointActionHandler = new ShowExecutionPointActionHandler();
  private final EvaluateActionHandler myEvaluateActionHandler = new EvaluateActionHandler();
  private final QuickEvaluateActionHandler myQuickEvaluateHandler = new QuickEvaluateActionHandler();
  private final JavaDebuggerSettingsPanelProvider myDebuggerSettingsPanelProvider = new JavaDebuggerSettingsPanelProvider();
  private final MuteBreakpointsActionHandler myMuteBreakpointsHandler = new MuteBreakpointsActionHandler();
  private final DebuggerActionHandler mySmartStepIntoHandler = new SmartStepIntoActionHandler();
  private final DebuggerActionHandler myAddToWatchedActionHandler = new AddToWatchActionHandler();

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

  @Override
  public AbstractDebuggerSession getCurrentSession(@NotNull Project project) {
    final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    return context != null ? context.getDebuggerSession() : null;
  }

  @NotNull
  public DebuggerSettingsPanelProvider getSettingsPanelProvider() {
    return myDebuggerSettingsPanelProvider;
  }

  private static class JavaBreakpointPanelProvider extends BreakpointPanelProvider<Breakpoint> {
    @NotNull
    public Collection<AbstractBreakpointPanel<Breakpoint>> getBreakpointPanels(@NotNull final Project project, @NotNull final DialogWrapper parentDialog) {
      List<AbstractBreakpointPanel<Breakpoint>> panels = new ArrayList<AbstractBreakpointPanel<Breakpoint>>();
      final BreakpointFactory[] allFactories = ApplicationManager.getApplication().getExtensions(BreakpointFactory.EXTENSION_POINT_NAME);
      for (BreakpointFactory factory : allFactories) {
        BreakpointPanel panel = factory.createBreakpointPanel(project, parentDialog);
        if (panel != null) {
          panel.setupPanelUI();
          panels.add(panel);
        }
      }
      return panels;
    }

    public int getPriority() {
      return 1;
    }

    public Breakpoint findBreakpoint(@NotNull final Project project, @NotNull final Document document, final int offset) {
      return DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().findBreakpoint(document, offset, null);
    }

    public void onDialogClosed(final Project project) {
      DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().updateAllRequests();
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

    public Collection<? extends Configurable> getConfigurables(final Project project) {
      final ArrayList<Configurable> configurables = new ArrayList<Configurable>();
      configurables.add(new DebuggerDataViewsConfigurable(project));
      configurables.add(new DebuggerSteppingConfigurable(project));
      configurables.add(new UserRenderersConfigurable(project));
      configurables.add(new DebuggerHotswapConfigurable());
      return configurables;
    }

    public void apply() {
      NodeRendererSettings.getInstance().fireRenderersChanged();
    }
  }
}
