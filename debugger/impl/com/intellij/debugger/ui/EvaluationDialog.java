package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.ui.impl.WatchDebuggerTree;
import com.intellij.debugger.ui.impl.WatchPanel;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.EvaluationDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.util.EventDispatcher;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class EvaluationDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.EvaluationDialog");
  private final MyEvaluationPanel myEvaluationPanel;
  private final ComboBox myCbFactories;
  private final Project myProject;
  private final DebuggerContextListener myContextListener;
  private final DebuggerEditorImpl myEditor;
  private final List<Runnable> myDisposeRunnables = new ArrayList<Runnable>();
  private final List<CodeFragmentFactory> myFactories = new ArrayList<CodeFragmentFactory>();

  public EvaluationDialog(Project project, TextWithImports text) {
    super(project, true);
    myProject = project;
    setModal(false);
    setCancelButtonText(DebuggerBundle.message("button.close.no.mnemonic"));
    setOKButtonText(DebuggerBundle.message("button.evaluate"));

    myEvaluationPanel = new MyEvaluationPanel(myProject);
    myCbFactories = new ComboBox(new MyComboBoxModel(), 150);
    myCbFactories.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final JLabel component = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        component.setText(((CodeFragmentFactory)value).getFileType().getLanguage().getID());
        return component;
      }
    });

    myCbFactories.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        myEditor.setFactory((CodeFragmentFactory)myCbFactories.getSelectedItem());
        myEditor.revalidate();
      }
    });

    CodeFragmentFactory factory = (CodeFragmentFactory)myCbFactories.getSelectedItem();
    if (factory == null) factory = DefaultCodeFragmentFactory.getInstance();
    myEditor = createEditor(factory);

    setDebuggerContext(getDebuggerContext());
    initDialogData(text);

    myContextListener = new DebuggerContextListener() {
      public void changeEvent(DebuggerContextImpl newContext, int event) {
        boolean close = true;
        for (DebuggerSession session : DebuggerManagerEx.getInstanceEx(myProject).getSessions()) {
          if (!session.isStopped()) {
            close = false;
            break;
          }
        }

        if(close) {
          close(CANCEL_EXIT_CODE);
        }
        else {
          setDebuggerContext(newContext);
        }
      }
    };
    DebuggerManagerEx.getInstanceEx(myProject).getContextManager().addListener(myContextListener);

    setHorizontalStretch(1f);
    setVerticalStretch(1f);
  }

  protected void doOKAction() {
    if (isOKActionEnabled()) {
      doEvaluate();
    }
  }

  protected void doEvaluate() {
    if (myEditor == null || myEvaluationPanel == null) {
      return;
    }

    myEvaluationPanel.clear();
    TextWithImports codeToEvaluate = getCodeToEvaluate();
    if (codeToEvaluate == null) {
      return;
    }
    try {
      setOKActionEnabled(false);
      NodeDescriptorImpl descriptor = myEvaluationPanel.getWatchTree().addWatch(codeToEvaluate).getDescriptor();
      if (descriptor instanceof EvaluationDescriptor) {
        final CodeFragmentFactory factory = ((MyComboBoxModel)myCbFactories.getModel()).getSelectedItem();
        LOG.assertTrue(factory != null); // there is always at least a default factory
        ((EvaluationDescriptor)descriptor).setCodeFragmentFactory(factory);
      }
      myEvaluationPanel.getWatchTree().rebuild(getDebuggerContext());
      descriptor.myIsExpanded = true;
    }
    finally {
      setOKActionEnabled(true);
    }
    getEditor().addRecent(getCodeToEvaluate());

    myEvaluationPanel.getContextManager().getContext().getDebuggerSession().refresh(true);
  }

  protected TextWithImports getCodeToEvaluate() {
    TextWithImports text = getEditor().getText();
    String s = text.getText();
    if (s != null) {
      s = s.trim();
    }
    if ("".equals(s)) {
      return null;
    }
    return text;
  }

  public JComponent getPreferredFocusedComponent() {
    return myEditor.getPreferredFocusedComponent();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.debugger.ui.EvaluationDialog2";
  }

  protected void addDisposeRunnable (Runnable runnable) {
    myDisposeRunnables.add(runnable);
  }

  public void dispose() {
    for (Runnable runnable : myDisposeRunnables) {
      runnable.run();
    }
    myDisposeRunnables.clear();
    myEditor.dispose();
    DebuggerManagerEx.getInstanceEx(myProject).getContextManager().removeListener(myContextListener);
    myEvaluationPanel.dispose();
    super.dispose();
  }

  protected class MyEvaluationPanel extends WatchPanel {
    public MyEvaluationPanel(final Project project) {
      super(project, (DebuggerManagerEx.getInstanceEx(project)).getContextManager());
      final WatchDebuggerTree watchTree = getWatchTree();
      final AnAction setValueAction  = ActionManager.getInstance().getAction(DebuggerActions.SET_VALUE);
      setValueAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), watchTree);
      registerDisposable(new Disposable() {
        public void dispose() {
          setValueAction.unregisterCustomShortcutSet(watchTree);
        }
      });
      setUpdateEnabled(true);
    }

    protected ActionPopupMenu createPopupMenu() {
      ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.EVALUATION_DIALOG_POPUP);
      return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.EVALUATION_DIALOG_POPUP, group);
    }

    protected void changeEvent(DebuggerContextImpl newContext, int event) {
      if (event == DebuggerSession.EVENT_REFRESH || event == DebuggerSession.EVENT_REFRESH_VIEWS_ONLY) {
        // in order not to spoil the evaluation result do not re-evaluate the tree
        final TreeModel treeModel = getTree().getModel();
        updateTree(treeModel, (DebuggerTreeNodeImpl)treeModel.getRoot());
      }
    }

    private void updateTree(final TreeModel model, final DebuggerTreeNodeImpl node) {
      if (node == null) {
        return;
      }
      final int count = model.getChildCount(node);
      for (int idx = 0; idx < count; idx++) {
        final DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)model.getChild(node, idx);
        updateTree(model, child);
      }
      node.labelChanged();
    }
  }

  protected void setDebuggerContext(DebuggerContextImpl context) {
    final PsiElement contextElement = PositionUtil.getContextElement(context);
    myFactories.clear();
    myFactories.addAll(DebuggerUtilsEx.getCodeFragmentFactories(contextElement));
    ((MyComboBoxModel)myCbFactories.getModel()).update();
    myCbFactories.setVisible(myCbFactories.getItemCount() > 1);
    myEditor.setContext(contextElement);
  }

  protected PsiElement getContext() {
    return myEditor.getContext();
  }

  protected void initDialogData(TextWithImports text) {
    getEditor().setText(text);
    myEvaluationPanel.clear();
  }

  public DebuggerContextImpl getDebuggerContext() {
    return DebuggerManagerEx.getInstanceEx(myProject).getContext();
  }

  public DebuggerEditorImpl getEditor() {
    return myEditor;
  }

  protected Component getCodeFragmentFactoryChooserComponent() {
    return myCbFactories;
  }

  protected abstract DebuggerEditorImpl createEditor(final CodeFragmentFactory factory);

  protected MyEvaluationPanel getEvaluationPanel() {
    return myEvaluationPanel;
  }

  public Project getProject() {
    return myProject;
  }

  private class MyComboBoxModel implements ComboBoxModel {
    private CodeFragmentFactory mySelectedItem = null;
    private EventDispatcher<ListDataListener> myDispatcher = EventDispatcher.create(ListDataListener.class);

    public void setSelectedItem(Object anItem) {
      if ((anItem instanceof CodeFragmentFactory && myFactories.contains(anItem))) {
        mySelectedItem = (CodeFragmentFactory)anItem;
      }
      else if (myFactories.size() > 0){
        mySelectedItem = myFactories.get(0);
      }
      else {
        mySelectedItem = null;
      }
    }

    public CodeFragmentFactory getSelectedItem() {
      return mySelectedItem;
    }

    public int getSize() {
      return myFactories.size();
    }

    public CodeFragmentFactory getElementAt(int index) {
      return myFactories.get(index);
    }

    public void addListDataListener(ListDataListener l) {
      myDispatcher.addListener(l);
    }

    public void removeListDataListener(ListDataListener l) {
      myDispatcher.removeListener(l);
    }

    void update() {
      setSelectedItem(mySelectedItem);
      myDispatcher.getMulticaster().contentsChanged(new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, -1, -1));
    }
  }
}
