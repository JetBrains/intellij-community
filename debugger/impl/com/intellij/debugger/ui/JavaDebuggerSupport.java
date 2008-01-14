package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.actions.*;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointFactory;
import com.intellij.debugger.ui.breakpoints.BreakpointPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.editor.Document;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.breakpoints.ui.AbstractBreakpointPanel;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

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
  }

  @NotNull
  public BreakpointPanelProvider<?> getBreakpointPanelProvider() {
    return myBreakpointPanelProvider;
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

    public Breakpoint findBreakpoint(@NotNull final Project project, @NotNull final Document document, final int offset) {
      return DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().findBreakpoint(document, offset, null);
    }

    public void onDialogClosed(final Project project) {
      DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().updateAllRequests();
    }
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
}
