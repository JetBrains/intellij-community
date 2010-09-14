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

/**
 * created at Dec 17, 2001
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.AddToWatchActionHandler;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.ui.DebuggerExpressionComboBox;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeInplaceEditor;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.ide.dnd.DropActionHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ListenerUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MainWatchPanel extends WatchPanel implements DataProvider {
  private final KeyStroke myRemoveWatchAccelerator = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
  private final KeyStroke myNewWatchAccelerator = KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0);
  private final KeyStroke myEditWatchAccelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0);

  public MainWatchPanel(Project project, DebuggerStateManager stateManager) {
    super(project,stateManager);
    final WatchDebuggerTree watchTree = getWatchTree();

    final AnAction removeWatchesAction = ActionManager.getInstance().getAction(DebuggerActions.REMOVE_WATCH);
    removeWatchesAction.registerCustomShortcutSet(new CustomShortcutSet(myRemoveWatchAccelerator), watchTree);

    final AnAction newWatchAction  = ActionManager.getInstance().getAction(DebuggerActions.NEW_WATCH);
    newWatchAction.registerCustomShortcutSet(new CustomShortcutSet(myNewWatchAccelerator), watchTree);

    final MouseAdapter mouseListener = new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
          AnAction editWatchAction = ActionManager.getInstance().getAction(DebuggerActions.EDIT_WATCH);
          Presentation presentation = (Presentation)editWatchAction.getTemplatePresentation().clone();
          DataContext context = DataManager.getInstance().getDataContext(watchTree);

          AnActionEvent actionEvent = new AnActionEvent(null, context, "WATCH_TREE", presentation, ActionManager.getInstance(), 0);
          editWatchAction.actionPerformed(actionEvent);
        }
      }
    };
    ListenerUtil.addMouseListener(watchTree, mouseListener);

    final AnAction editWatchAction  = ActionManager.getInstance().getAction(DebuggerActions.EDIT_WATCH);
    editWatchAction.registerCustomShortcutSet(new CustomShortcutSet(myEditWatchAccelerator), watchTree);
    registerDisposable(new Disposable() {
      public void dispose() {
        ListenerUtil.removeMouseListener(watchTree, mouseListener);
        removeWatchesAction.unregisterCustomShortcutSet(watchTree);
        newWatchAction.unregisterCustomShortcutSet(watchTree);
        editWatchAction.unregisterCustomShortcutSet(watchTree);
      }
    });

    DnDManager.getInstance().registerTarget(new DnDNativeTarget() {
      public boolean update(final DnDEvent aEvent) {
        Object object = aEvent.getAttachedObject();
        if (object == null) return true;

        String add = DebuggerBundle.message("watchs.add.text");

        if (object.getClass().isArray()) {
          Class<?> type = object.getClass().getComponentType();
          if (DebuggerTreeNodeImpl.class.isAssignableFrom(type)) {
            aEvent.setHighlighting(myTree, DnDEvent.DropTargetHighlightingType.RECTANGLE | DnDEvent.DropTargetHighlightingType.TEXT);
            aEvent.setDropPossible(add, new DropActionHandler() {
              public void performDrop(final DnDEvent aEvent) {
                addWatchesFrom((DebuggerTreeNodeImpl[])aEvent.getAttachedObject());
              }
            });
          }
        } else if (object instanceof EventInfo) {
          EventInfo info = (EventInfo)object;
          final String text = info.getTextForFlavor(DataFlavor.stringFlavor);
          if (text != null) {
            aEvent.setHighlighting(myTree, DnDEvent.DropTargetHighlightingType.RECTANGLE | DnDEvent.DropTargetHighlightingType.TEXT);
            aEvent.setDropPossible(add, new DropActionHandler() {
              public void performDrop(final DnDEvent aEvent) {
                addWatchesFrom(text);
              }
            });
          }
        }

        return true;
      }

      public void drop(final DnDEvent aEvent) {
      }

      public void cleanUpOnLeave() {
      }

      public void updateDraggedImage(final Image image, final Point dropPoint, final Point imageOffset) {
      }
    }, myTree);
  }

  private void addWatchesFrom(final DebuggerTreeNodeImpl[] nodes) {
    AddToWatchActionHandler.addFromNodes(getContext(), this, nodes);
  }

  private void addWatchesFrom(String text) {
    AddToWatchActionHandler.doAddWatch(this, new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, text), null);
  }

  protected ActionPopupMenu createPopupMenu() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.WATCH_PANEL_POPUP);
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(DebuggerActions.WATCH_PANEL_POPUP, group);
    return popupMenu;
  }

  public void newWatch() {
    final DebuggerTreeNodeImpl node = getWatchTree().addWatch(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, ""));
    editNode(node);
  }

  public void editNode(final DebuggerTreeNodeImpl node) {
    final DebuggerContextImpl context = getContext();
    final DebuggerExpressionComboBox comboBox = new DebuggerExpressionComboBox(getProject(), PositionUtil.getContextElement(context), "evaluation",
                                                                               DefaultCodeFragmentFactory.getInstance());
    comboBox.setText(((WatchItemDescriptor)node.getDescriptor()).getEvaluationText());
    comboBox.selectAll();

    DebuggerTreeInplaceEditor editor = new DebuggerTreeInplaceEditor(node) {
      public JComponent createInplaceEditorComponent() {
        return comboBox;
      }

      public JComponent getPreferredFocusedComponent() {
        return comboBox.getPreferredFocusedComponent();
      }

      public Editor getEditor() {
        return comboBox.getEditor();
      }

      public JComponent getEditorComponent() {
        return comboBox.getEditorComponent();
      }

      public void doOKAction() {
        if (comboBox.isPopupVisible()) {
          comboBox.selectPopupValue();
        }

        TextWithImports text = comboBox.getText();
        WatchDebuggerTree.setWatchNodeText(node, text);
        comboBox.addRecent(text);
        try {
          super.doOKAction();
        }
        finally {
          comboBox.dispose();
        }
      }

      public void cancelEditing() {
        comboBox.setPopupVisible(false);

        try {
          super.cancelEditing();
        }
        finally {
          comboBox.dispose();
        }
      }
    };
    editor.show();
  }
}
