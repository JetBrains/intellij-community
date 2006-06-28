package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.ui.DebuggerExpressionComboBox;
import com.intellij.debugger.ui.DebuggerExpressionTextField;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.CommonBundle;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

import org.jetbrains.annotations.NonNls;

/**
 * created Jun 18, 2001
 * @author Jeka
 */
public class BreakpointsConfigurationDialogFactory {
  private static final @NonNls String BREAKPOINT_PANEL = "breakpoint_panel";
  private Project myProject;

  private int myLastSelectedTabIndex = 0;

  public BreakpointsConfigurationDialogFactory(Project project) {
    myProject = project;
  }

  private BreakpointManager getBreakpointManager() {
    return DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
  }

  public DialogWrapper createDialog(Breakpoint initialBreakpoint, String selectComponent) {
    BreakpointsConfigurationDialog dialog = new BreakpointsConfigurationDialog();
    dialog.selectBreakpoint(initialBreakpoint);
    dialog.setPreferredFocusedComponent(selectComponent);
    return dialog;
  }

  private class BreakpointsConfigurationDialog extends DialogWrapper {
    private JPanel myPanel;
    private TabbedPaneWrapper myTabbedPane;
    private JComponent myPreferredComponent;
    private java.util.List<Runnable> myDisposeActions = new ArrayList<Runnable>();
    private java.util.List<BreakpointPanel> myPanels = new ArrayList<BreakpointPanel>();

    public BreakpointsConfigurationDialog() {
      super(myProject, true);
      setTitle(DebuggerBundle.message("breakpoints.configuration.dialog.title"));
      setOKButtonText(CommonBundle.message("button.close"));
      init();
      reset();
    }

    protected Action[] createActions(){
      return new Action[]{getOKAction(), getHelpAction()};
    }

    protected void doHelpAction() {
      final JComponent selectedComponent = myTabbedPane.getSelectedComponent();
      BreakpointPanel currentPanel = null;
      for (BreakpointPanel breakpointPanel : myPanels) {
        if (selectedComponent == breakpointPanel.getPanel()) {
          currentPanel = breakpointPanel;
          break;
        }
      }
      if (currentPanel != null && currentPanel.getHelpID() != null) {
        HelpManager.getInstance().invokeHelp(currentPanel.getHelpID());
      }
      else {
        super.doHelpAction();
      }
    }

    protected JComponent createCenterPanel() {
      myTabbedPane = new TabbedPaneWrapper();
      myPanel = new JPanel(new BorderLayout());

      final BreakpointFactory[] allFactories = ApplicationManager.getApplication().getComponents(BreakpointFactory.class);
      for (final BreakpointFactory breakpointFactory : allFactories) {
        final BreakpointPanel breakpointPanel = breakpointFactory.createBreakpointPanel(myProject, this);
        if (breakpointPanel != null) {
          setupPanelUI(breakpointPanel);
          myPanels.add(breakpointPanel);
          addPanel(breakpointPanel, breakpointPanel.getDisplayName());
        }
      }

      final ChangeListener tabPaneChangeListener = new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          BreakpointPanel panel = getSelectedPanel();
          panel.ensureSelectionExists();
        }
      };
      myTabbedPane.addChangeListener(tabPaneChangeListener);
      myDisposeActions.add(new Runnable() {
        public void run() {
          myTabbedPane.removeChangeListener(tabPaneChangeListener);
        }
      });
      myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);

      // "Enter" and "Esc" keys work like "Close" button.
      ActionListener closeAction = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          close(CANCEL_EXIT_CODE);
        }
      };
      myPanel.registerKeyboardAction(
        closeAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );
      myPanel.setPreferredSize(new Dimension(600, 500));
      return myPanel;
    }

    @SuppressWarnings({"HardCodedStringLiteral"}) private void setupPanelUI(BreakpointPanel panel) {
      final BreakpointManager breakpointManager = getBreakpointManager();
      final String category = panel.getBreakpointCategory();
      final BreakpointTree tree = panel.getTree();
      final String flattenPackages = breakpointManager.getProperty(category + "_flattenPackages");
      if (flattenPackages != null) {
        tree.setFlattenPackages("true".equalsIgnoreCase(flattenPackages));
      }
      final String groupByClasses = breakpointManager.getProperty(category + "_groupByClasses");
      if (groupByClasses != null) {
        tree.setGroupByClasses("true".equalsIgnoreCase(groupByClasses));
      }
      final String groupByMethods = breakpointManager.getProperty(category + "_groupByMethods");
      if (groupByMethods != null) {
        tree.setGroupByMethods("true".equalsIgnoreCase(groupByMethods));
      }

      final String viewId = breakpointManager.getProperty(category + "_viewId");
      if (viewId != null) {
        panel.showView(viewId);
      }
    }

    @SuppressWarnings({"HardCodedStringLiteral"}) private void savePanelSettings(BreakpointPanel panel, String category) {
      final BreakpointManager breakpointManager = getBreakpointManager();

      final BreakpointTree tree = panel.getTree();
      breakpointManager.setProperty(category + "_flattenPackages", tree.isFlattenPackages()? "true" : "false");
      breakpointManager.setProperty(category + "_groupByClasses", tree.isGroupByClasses()? "true" : "false");
      breakpointManager.setProperty(category + "_groupByMethods", tree.isGroupByMethods()? "true" : "false");
      breakpointManager.setProperty(category + "_viewId", panel.getCurrentViewId());
    }

    private void addPanel(final BreakpointPanel panel, final String title) {
      JPanel jpanel = panel.getPanel();
      jpanel.putClientProperty(BREAKPOINT_PANEL, panel);
      myTabbedPane.addTab(title, jpanel);
      final int tabIndex = myTabbedPane.getTabCount() - 1;
      final BreakpointPanel.ChangesListener changesListener = new BreakpointPanel.ChangesListener() {
        public void breakpointsChanged() {
          updateTabTitle(tabIndex);
        }
      };
      panel.addChangesListener(changesListener);
      myDisposeActions.add(new Runnable() {
        public void run() {
          panel.removeChangesListener(changesListener);
        }
      });
    }

    private BreakpointPanel getSelectedPanel() {
      JComponent selectedComponent = myTabbedPane.getSelectedComponent();
      return selectedComponent != null ? (BreakpointPanel)selectedComponent.getClientProperty(BREAKPOINT_PANEL) : null;
    }

    public JComponent getPreferredFocusedComponent() {
      if (myPreferredComponent != null) {
        if (myPreferredComponent instanceof DebuggerExpressionComboBox) {
          ((DebuggerExpressionComboBox)myPreferredComponent).selectAll();
        }
        else if (myPreferredComponent instanceof DebuggerExpressionTextField) {
          ((DebuggerExpressionTextField)myPreferredComponent).selectAll();
        }
        return myPreferredComponent;
      }
      return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myTabbedPane.getComponent());
    }

    public void setPreferredFocusedComponent(String control) {
      BreakpointPanel selectedPanel = getSelectedPanel();
      myPreferredComponent = selectedPanel.getControl(control);
    }

    public void dispose() {
      apply();
      for (Runnable runnable : myDisposeActions) {
        runnable.run();
      }
      myDisposeActions.clear();
      if (myPanel != null) {
        for (BreakpointPanel panel : myPanels) {
          savePanelSettings(panel, panel.getBreakpointCategory());
          panel.dispose();
        }
        myLastSelectedTabIndex = myTabbedPane.getSelectedIndex();
        myPanel.removeAll();
        myPanel = null;
        myTabbedPane = null;
      }
      super.dispose();
    }

    private void apply() {
      for (BreakpointPanel panel : myPanels) {
        panel.saveChanges();
      }
      DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().updateAllRequests();
    }

    private void reset() {
      final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
      for (BreakpointPanel panel : myPanels) {
        panel.setBreakpoints(breakpointManager.getBreakpoints(panel.getBreakpointCategory()));
      }
      updateAllTabTitles();
      if (myLastSelectedTabIndex >= myTabbedPane.getTabCount() && myLastSelectedTabIndex < 0) {
        myLastSelectedTabIndex = 0;
      }
      myTabbedPane.setSelectedIndex(myLastSelectedTabIndex);
    }

    private void selectBreakpoint(Breakpoint breakpoint) {
      if (breakpoint == null) {
        return;
      }
      final String category = breakpoint.getCategory();
      for (BreakpointPanel breakpointPanel : myPanels) {
        if (category.equals(breakpointPanel.getBreakpointCategory())) {
          myTabbedPane.setSelectedComponent(breakpointPanel.getPanel());
          breakpointPanel.selectBreakpoint(breakpoint);
          break;
        }
      }
    }

    private void updateAllTabTitles() {
      for (int idx = 0; idx < myTabbedPane.getTabCount(); idx++) {
        updateTabTitle(idx);
      }
    }

    private void updateTabTitle(final int idx) {
      JComponent component = myTabbedPane.getComponentAt(idx);
      for (BreakpointPanel breakpointPanel : myPanels) {
        if (component == breakpointPanel.getPanel()) {
          final BreakpointFactory factory = BreakpointFactory.getInstance(breakpointPanel.getBreakpointCategory());
          myTabbedPane.setIconAt(idx, hasEnabledBreakpoints(breakpointPanel)? factory.getIcon() : factory.getDisabledIcon());
          break;
        }
      }
    }

    private boolean hasEnabledBreakpoints(BreakpointPanel panel) {
      final java.util.List<Breakpoint> breakpoints = panel.getBreakpoints();
      for (Breakpoint breakpoint : breakpoints) {
        if (breakpoint.ENABLED) {
          return true;
        }
      }
      return false;
    }

  }

}
