package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.settings.DebuggerGeneralConfigurable;
import com.intellij.debugger.settings.UserRenderersConfigurable;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.actions.*;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointFactory;
import com.intellij.debugger.ui.breakpoints.BreakpointPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.options.Configurable;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.settings.DebuggerSettingsPanelProvider;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import com.intellij.xdebugger.impl.breakpoints.ui.AbstractBreakpointPanel;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class JavaDebuggerSupport extends DebuggerSupport {
  private JavaBreakpointPanelProvider myBreakpointPanelProvider;
  private StepOverActionHandler myStepOverActionHandler;
  private StepIntoActionHandler myStepIntoActionHandler;
  private StepOutActionHandler myStepOutActionHandler;
  private ForceStepOverActionHandler myForceStepOverActionHandler;
  private ForceStepIntoActionHandler myForceStepIntoActionHandler;
  private RunToCursorActionHandler myRunToCursorActionHandler;
  private ForceRunToCursorActionHandler myForceRunToCursorActionHandler;
  private ResumeActionHandler myResumeActionHandler;
  private PauseActionHandler myPauseActionHandler;
  private ToggleLineBreakpointActionHandler myToggleLineBreakpointActionHandler;
  private ShowExecutionPointActionHandler myShowExecutionPointActionHandler;
  private EvaluateActionHandler myEvaluateActionHandler;
  private QuickEvaluateActionHandler myQuickEvaluateHandler;
  private JavaDebuggerSettingsPanelProvider myDebuggerSettingsPanelProvider;
  private MuteBreakpointsActionHandler myMuteBreakpointsHandler;

  public JavaDebuggerSupport() {
    myBreakpointPanelProvider = new JavaBreakpointPanelProvider();
    myStepOverActionHandler = new StepOverActionHandler();
    myStepIntoActionHandler = new StepIntoActionHandler();
    myStepOutActionHandler = new StepOutActionHandler();
    myForceStepOverActionHandler = new ForceStepOverActionHandler();
    myForceStepIntoActionHandler = new ForceStepIntoActionHandler();
    myRunToCursorActionHandler = new RunToCursorActionHandler();
    myForceRunToCursorActionHandler = new ForceRunToCursorActionHandler();
    myResumeActionHandler = new ResumeActionHandler();
    myPauseActionHandler = new PauseActionHandler();
    myToggleLineBreakpointActionHandler = new ToggleLineBreakpointActionHandler();
    myShowExecutionPointActionHandler = new ShowExecutionPointActionHandler();
    myEvaluateActionHandler = new EvaluateActionHandler();
    myQuickEvaluateHandler = new QuickEvaluateActionHandler();
    myDebuggerSettingsPanelProvider = new JavaDebuggerSettingsPanelProvider();
    myMuteBreakpointsHandler = new MuteBreakpointsActionHandler();
  }

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

  public DebuggerToggleActionHandler getMuteBreakpointsHandler() {
    return myMuteBreakpointsHandler;
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

    public Collection<? extends Configurable> getConfigurables(final Project project) {
      ArrayList<Configurable> configurables = new ArrayList<Configurable>();
      configurables.add(new DebuggerGeneralConfigurable(project));
      configurables.add(new UserRenderersConfigurable(project));
      return configurables;
    }

    public void apply() {
      NodeRendererSettings.getInstance().fireRenderersChanged();
    }
  }
}
