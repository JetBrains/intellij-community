package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.ui.breakpoints.actions.BreakpointPanelAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.EventDispatcher;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.EventListener;

/**
 * @author Jeka
 */
public class BreakpointPanel {
  private final BreakpointPropertiesPanel myPropertiesPanel;
  private final BreakpointPanelAction[] myActions;
  private final String myBreakpointCategory;
  private final String myDisplayName;
  private final String myHelpID;
  private Breakpoint myCurrentViewableBreakpoint;
  private java.util.List<Runnable> myDisposeActions = new ArrayList<Runnable>();

  private JPanel myPanel;
  private JPanel myBreakPointsPanel;
  private JPanel myTablePlace;
  private JPanel myPropertiesPanelPlace;
  private BreakpointTable myTable;
  private BreakpointTree myTree;
  private JPanel myButtonsPanel;
  private EventDispatcher<ChangesListener> myEventDispatcher = EventDispatcher.create(ChangesListener.class);
  private String myCurrentViewId = TABLE_VIEW;

  private static final @NonNls String PROPERTIES_STUB = "STUB";
  private static final @NonNls String PROPERTIES_DATA = "DATA";

  public static final @NonNls String TREE_VIEW = "TREE";
  public static final @NonNls String TABLE_VIEW = "TABLE";

  public BreakpointTable getTable() {
    return myTable;
  }

  public BreakpointTree getTree() {
    return myTree;
  }

  public void switchViews() {
    final Breakpoint[] selectedBreakpoints = getSelectedBreakpoints();
    showView(isTreeShowing() ? TABLE_VIEW : TREE_VIEW);
    selectBreakpoints(selectedBreakpoints);
  }

  public void showView(final String viewId) {
    if (TREE_VIEW.equals(viewId) || TABLE_VIEW.equals(viewId)) {
      myCurrentViewId = viewId;
      ((CardLayout)myTablePlace.getLayout()).show(myTablePlace, viewId);
      updateButtons();
      ensureSelectionExists();
    }
  }

  public String getCurrentViewId() {
    return myCurrentViewId;
  }

  public boolean isTreeShowing() {
    return BreakpointPanel.TREE_VIEW.equals(getCurrentViewId());
  }

  public String getHelpID() {
    return myHelpID;
  }

  public interface ChangesListener extends EventListener {
    void breakpointsChanged();
  }

  public void addChangesListener(ChangesListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeChangesListener(ChangesListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public BreakpointPanel(final Project project,
                         BreakpointPropertiesPanel propertiesPanel,
                         final BreakpointPanelAction[] actions,
                         String breakpointCategory,
                         String displayName,
                         String helpId) {
    myPropertiesPanel = propertiesPanel;
    myActions = actions;
    myBreakpointCategory = breakpointCategory;
    myDisplayName = displayName;
    myHelpID = helpId;

    myTable = new BreakpointTable(project);
    myTree = new BreakpointTree(project);

    myTablePlace.setLayout(new CardLayout());
    myTablePlace.add(ScrollPaneFactory.createScrollPane(myTable), TABLE_VIEW);

    final ListSelectionListener listSelectionListener = new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateCurrentBreakpointPropertiesPanel();
      }
    };
    final ListSelectionModel tableSelectionModel = myTable.getSelectionModel();
    tableSelectionModel.addListSelectionListener(listSelectionListener);
    myDisposeActions.add(new Runnable() {
      public void run() {
        tableSelectionModel.removeListSelectionListener(listSelectionListener);
      }
    });

    final TreeSelectionModel treeSelectionModel = myTree.getSelectionModel();
    final TreeSelectionListener treeSelectionListener = new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        updateCurrentBreakpointPropertiesPanel();
      }
    };
    treeSelectionModel.addTreeSelectionListener(treeSelectionListener);
    myDisposeActions.add(new Runnable() {
      public void run() {
        treeSelectionModel.removeTreeSelectionListener(treeSelectionListener);
      }
    });

    final BreakpointTableModel tableModel = myTable.getModel();
    final TableModelListener tableModelListener = new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        if (e.getType() == TableModelEvent.UPDATE) {
          updateCurrentBreakpointPropertiesPanel();
        }
        myEventDispatcher.getMulticaster().breakpointsChanged();
      }
    };
    tableModel.addTableModelListener(tableModelListener);
    myDisposeActions.add(new Runnable() {
      public void run() {
        tableModel.removeTableModelListener(tableModelListener);
      }
    });


    final TreeModel treeModel = myTree.getModel();
    final TreeModelListener treeModelListener = new TreeModelListener() {
      public void treeNodesChanged(TreeModelEvent e) {
      }

      public void treeNodesInserted(TreeModelEvent e) {
      }

      public void treeNodesRemoved(TreeModelEvent e) {
      }

      public void treeStructureChanged(TreeModelEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            ensureSelectionExists();
            updateButtons();
          }
        });
      }
    };
    treeModel.addTreeModelListener(treeModelListener);
    myDisposeActions.add(new Runnable() {
      public void run() {
        treeModel.removeTreeModelListener(treeModelListener);
      }
    });

    myPropertiesPanelPlace.setLayout(new CardLayout());
    final JPanel stubPanel = new JPanel();
    stubPanel.setMinimumSize(myPropertiesPanel.getPanel().getMinimumSize());
    myPropertiesPanelPlace.add(stubPanel, PROPERTIES_STUB);
    myPropertiesPanelPlace.add(myPropertiesPanel.getPanel(), PROPERTIES_DATA);

    myBreakPointsPanel.setBorder(IdeBorderFactory.createEmptyBorder(6, 6, 0, 6));

    myButtonsPanel.setLayout(new GridBagLayout());
    for (int idx = 0; idx < actions.length; idx++) {
      final BreakpointPanelAction action = actions[idx];
      action.setPanel(this);
      final AbstractButton button = action.isStateAction()? new JCheckBox(action.getName()) : new JButton(action.getName());
      action.setButton(button);
      button.addActionListener(action);
      myDisposeActions.add(new Runnable() {
        public void run() {
          button.removeActionListener(action);
        }
      });
      final double weighty = (idx == actions.length - 1) ? 1.0 : 0.0;
      myButtonsPanel.add(button, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, weighty, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 2, 2, 2), 0, 0));
    }
    final ListSelectionListener tableSelectionListener = new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    };
    tableSelectionModel.addListSelectionListener(tableSelectionListener);
    myDisposeActions.add(new Runnable() {
      public void run() {
        tableSelectionModel.removeListSelectionListener(tableSelectionListener);
      }
    });

    myTablePlace.add(ScrollPaneFactory.createScrollPane(myTree), TREE_VIEW);

    updateCurrentBreakpointPropertiesPanel();

    final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
    final BreakpointManagerListener breakpointManagerListener = new BreakpointManagerListener() {
      public void breakpointsChanged() {
        if (isTreeShowing()) {
          myTree.repaint();
        }
        else {
          myTable.repaint();
        }
      }
    };
    breakpointManager.addBreakpointManagerListener(breakpointManagerListener);
    myDisposeActions.add(new Runnable() {
      public void run() {
        breakpointManager.removeBreakpointManagerListener(breakpointManagerListener);
      }
    });
  }

  public String getBreakpointCategory() {
    return myBreakpointCategory;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public Breakpoint getCurrentViewableBreakpoint() {
    return myCurrentViewableBreakpoint;
  }

  public void saveChanges() {
    if (myCurrentViewableBreakpoint != null) {
      myPropertiesPanel.saveTo(myCurrentViewableBreakpoint, new Runnable() {
        public void run() {
          myTable.repaint();
        }
      });
    }
  }

  public void updateButtons() {
    for (final BreakpointPanelAction action : myActions) {
      final AbstractButton button = action.getButton();
      action.update();
      if (!button.isEnabled() && button.hasFocus()) {
        button.transferFocus();
      }
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void selectBreakpoint(Breakpoint breakpoint) {
    if (isTreeShowing()) {
      myTree.selectBreakpoint(breakpoint);
    }
    else {
      int index = myTable.getModel().getBreakpointIndex(breakpoint);
      ListSelectionModel model = myTable.getSelectionModel();
      model.clearSelection();
      model.addSelectionInterval(index, index);
    }
  }

  public void selectBreakpoints(Breakpoint[] breakpoints) {
    if (isTreeShowing()) {
      myTree.selectBreakpoints(breakpoints);
    }
    else {
      final TIntArrayList rows = new TIntArrayList(breakpoints.length);
      for (Breakpoint breakpoint : breakpoints) {
        final int index = myTable.getModel().getBreakpointIndex(breakpoint);
        if (index >= 0) {
          rows.add(index);
        }
      }
      myTable.getSelectionModel().clearSelection();
      TableUtil.selectRows(myTable, rows.toNativeArray());
    }
  }

  public void setBreakpoints(Breakpoint[] breakpoints) {
    myTable.setBreakpoints(breakpoints);
    myTree.setBreakpoints(breakpoints);
    ensureSelectionExists();
    updateButtons();
  }

  public Breakpoint[] getSelectedBreakpoints() {
    return isTreeShowing() ? myTree.getSelectedBreakpoints() : myTable.getSelectedBreakpoints();
  }

  public void removeSelectedBreakpoints() {
    final Breakpoint[] selectedBreakpoints = getSelectedBreakpoints();
    Breakpoint breakpointToSelect = null;
    if (selectedBreakpoints.length > 0) {
      if (isTreeShowing()) {
        breakpointToSelect = myTree.getPreviousSibling(selectedBreakpoints[0]);
      }
      else {
        final int index = myTable.getModel().getBreakpointIndex(selectedBreakpoints[0]) - 1;
        if (index >= 0 && index < (myTable.getRowCount() - selectedBreakpoints.length)) {
          breakpointToSelect = myTable.getModel().getBreakpoint(index);
        }
      }
    }
    myTree.removeBreakpoints(selectedBreakpoints);
    myTable.getModel().removeBreakpoints(selectedBreakpoints);
    myCurrentViewableBreakpoint = null;
    if (breakpointToSelect != null) {
      selectBreakpoint(breakpointToSelect);
    }
    else {
      updateCurrentBreakpointPropertiesPanel();
    }
  }

  public void insertBreakpointAt(Breakpoint breakpoint, int index) {
    myTable.getModel().insertBreakpointAt(breakpoint, index);
    myTree.addBreakpoint(breakpoint);
    selectBreakpoint(breakpoint);
  }

  public void addBreakpoint(Breakpoint breakpoint) {
    myTable.getModel().addBreakpoint(breakpoint);
    myTree.addBreakpoint(breakpoint);
    selectBreakpoint(breakpoint);
  }

  private void updateCurrentBreakpointPropertiesPanel() {
    if (myCurrentViewableBreakpoint != null) {
      myPropertiesPanel.saveTo(myCurrentViewableBreakpoint, new Runnable() {
        public void run() {
          if (isTreeShowing()) {
            myTree.repaint();
          }
          else {
            myTable.repaint();
          }
        }
      });
    }
    Breakpoint[] breakpoints = getSelectedBreakpoints();
    Breakpoint oldViewableBreakpoint = myCurrentViewableBreakpoint;
    myCurrentViewableBreakpoint = (breakpoints != null && breakpoints.length == 1) ? breakpoints[0] : null;
    if (myCurrentViewableBreakpoint != null) {
      if (oldViewableBreakpoint == null) {
        ((CardLayout)myPropertiesPanelPlace.getLayout()).show(myPropertiesPanelPlace, PROPERTIES_DATA);
      }
      myPropertiesPanel.initFrom(myCurrentViewableBreakpoint);
    }
    else {
      ((CardLayout)myPropertiesPanelPlace.getLayout()).show(myPropertiesPanelPlace, PROPERTIES_STUB);
    }
    updateButtons();
  }

  public JComponent getControl(String control) {
    return myPropertiesPanel.getControl(control);
  }

  public int getBreakpointCount() {
    return myTable.getBreakpoints().size();
  }

  public final java.util.List<Breakpoint> getBreakpoints() {
    return myTable.getBreakpoints();
  }

  public void dispose() {
    for (Runnable runnable : myDisposeActions) {
      runnable.run();
    }
    myDisposeActions.clear();

    myTree.dispose();

    final KeyStroke[] tableStrokes = myTable.getRegisteredKeyStrokes();
    for (KeyStroke stroke : tableStrokes) {
      myTable.unregisterKeyboardAction(stroke);
    }

    myPropertiesPanel.dispose();
  }

  public OpenFileDescriptor createEditSourceDescriptor(final Project project) {
    Breakpoint[] breakpoints = getSelectedBreakpoints();
    if (breakpoints == null || breakpoints.length == 0) {
      return null;
    }
    Breakpoint br = breakpoints[0];
    int line;
    Document doc;
    if (br instanceof BreakpointWithHighlighter) {
      BreakpointWithHighlighter breakpoint = (BreakpointWithHighlighter)br;
      doc = breakpoint.getDocument();
      line = breakpoint.getLineIndex();
    }
    else {
      return null;
    }
    if (line < 0 || line >= doc.getLineCount()) {
      return null;
    }
    int offset = doc.getLineStartOffset(line);
    if(br instanceof FieldBreakpoint) {
      PsiField field = ((FieldBreakpoint) br).getPsiField();
      if(field != null) {
        offset = field.getTextOffset();
      }
    }
    VirtualFile vFile = FileDocumentManager.getInstance().getFile(doc);
    if (vFile == null || !vFile.isValid()) {
      return null;
    }
    return new OpenFileDescriptor(project, vFile, offset);
  }

  public void ensureSelectionExists() {
    if (myTable.getRowCount() > 0 && myTable.getSelectedRow() < 0) {
      ListSelectionModel model = myTable.getSelectionModel();
      model.clearSelection();
      model.addSelectionInterval(0, 0);
    }

    final java.util.List<Breakpoint> treeBreakpoints = myTree.getBreakpoints();
    if (treeBreakpoints.size() > 0) {
      if (myTree.getSelectionModel().getSelectionCount() == 0) {
        myTree.selectFirstBreakpoint();
      }
    }

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (isTreeShowing()) {
          myTree.requestFocus();
        }
        else {
          myTable.requestFocus();
        }
      }
    });
  }

}