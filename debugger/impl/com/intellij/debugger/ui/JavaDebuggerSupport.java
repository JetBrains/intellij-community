package com.intellij.debugger.ui;

import com.intellij.debugger.ui.breakpoints.BreakpointFactory;
import com.intellij.debugger.ui.breakpoints.BreakpointPanel;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.breakpoints.ui.AbstractBreakpointPanel;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class JavaDebuggerSupport extends DebuggerSupport {
  private JavaBreakpointPanelProvider myBreakpointPanelProvider = new JavaBreakpointPanelProvider();

  @NotNull
  public BreakpointPanelProvider getBreakpointPanelProvider() {
    return myBreakpointPanelProvider;
  }

  private static class JavaBreakpointPanelProvider extends BreakpointPanelProvider {
    @NotNull
    public AbstractBreakpointPanel[] getBreakpointPanels(@NotNull final Project project, @NotNull final DialogWrapper parentDialog) {
      List<AbstractBreakpointPanel> panels = new ArrayList<AbstractBreakpointPanel>();
      final BreakpointFactory[] allFactories = ApplicationManager.getApplication().getExtensions(BreakpointFactory.EXTENSION_POINT_NAME);
      for (BreakpointFactory factory : allFactories) {
        BreakpointPanel panel = factory.createBreakpointPanel(project, parentDialog);
        if (panel != null) {
          panel.setupPanelUI();
          panels.add(panel);
        }
      }
      return panels.toArray(new AbstractBreakpointPanel[panels.size()]);
    }

    public void onDialogClosed(final Project project) {
      DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().updateAllRequests();
    }
  }
}
