/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;

public abstract class InplaceEditor implements AWTEventListener{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.InplaceEditor");
  private final DebuggerTreeNodeImpl myNode;
  private JComponent myInplaceEditorComponent;

  private ComponentAdapter myComponentListener = new ComponentAdapter() {
    public void componentResized(ComponentEvent e) {
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          if (!isShown()) {
            return;
          }
          JTree tree = myNode.getTree();
          JLayeredPane layeredPane = tree.getRootPane().getLayeredPane();
          Rectangle bounds = getEditorBounds();
          Point layeredPanePoint=SwingUtilities.convertPoint(tree, bounds.x, bounds.y,layeredPane);
          myInplaceEditorComponent.setBounds(layeredPanePoint.x,layeredPanePoint.y, bounds.width, bounds.height);
          myInplaceEditorComponent.revalidate();
        }
      });
    }

    public void componentHidden(ComponentEvent e) {
      cancelEditing();
    }
  };

  private RunContentListener myRunContentListener = new RunContentListener() {
    public void contentSelected(RunContentDescriptor descriptor) {
      cancelEditing();
    }

    public void contentRemoved(RunContentDescriptor descriptor) {
      cancelEditing();
    }
  };

  protected abstract JComponent createInplaceEditorComponent();

  protected abstract JComponent getPreferredFocusedComponent();

  public abstract Editor getEditor();

  public abstract JComponent getEditorComponent();

  public void doOKAction() {
    remove();
  }

  public void cancelEditing() {
    remove();
  }

  private void remove() {
    if (!isShown()) {
      return;
    }
    //ListenerUtil.removeKeyListener(getPreferredFocusedComponent(), this);
    //ListenerUtil.removeKeyListener(myInplaceEditorComponent, this);
    Toolkit.getDefaultToolkit().removeAWTEventListener(this);
    ExecutionManager.getInstance(getProject()).getContentManager().removeRunContentListener(myRunContentListener);

    DebuggerTree tree = myNode.getTree();
    JRootPane rootPane = tree.getRootPane();
    if (rootPane != null) {
      JLayeredPane layeredPane = rootPane.getLayeredPane();
      if(layeredPane != null) {
        layeredPane.remove(myInplaceEditorComponent);
      }
      rootPane.removeComponentListener(myComponentListener);
    }
    myInplaceEditorComponent = null;
    tree.repaint();
    tree.requestFocus();
  }

  private Project getProject() {
    return myNode.getTree().getProject();
  }

  public InplaceEditor(DebuggerTreeNodeImpl node) {
    myNode = node;
  }

  public DebuggerTreeNodeImpl getNode() {
    return myNode;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void show() {
    LOG.assertTrue(myInplaceEditorComponent == null, "editor is not released");
    final DebuggerTree tree = myNode.getTree();
    final JLayeredPane layeredPane = tree.getRootPane().getLayeredPane();

    Rectangle bounds = getEditorBounds();

    Point layeredPanePoint = SwingUtilities.convertPoint(tree, bounds.x, bounds.y,layeredPane);

    myInplaceEditorComponent = createInplaceEditorComponent();
    LOG.assertTrue(myInplaceEditorComponent != null);
    myInplaceEditorComponent.setBounds(
      layeredPanePoint.x,
      layeredPanePoint.y,
      bounds.width,
      Math.max(bounds.height, myInplaceEditorComponent.getPreferredSize().height)
    );

    layeredPane.add(myInplaceEditorComponent,new Integer(250));

    myInplaceEditorComponent.validate();
    myInplaceEditorComponent.paintImmediately(0,0,myInplaceEditorComponent.getWidth(),myInplaceEditorComponent.getHeight());
    getPreferredFocusedComponent().requestFocus();

    tree.getRootPane().addComponentListener(myComponentListener);
    ExecutionManager.getInstance(getProject()).getContentManager().addRunContentListener(myRunContentListener);
    final JComponent editorComponent = getEditorComponent();
    editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enterStroke");
    editorComponent.getActionMap().put("enterStroke", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        doOKAction();
      }
    });
    editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeStroke");
    editorComponent.getActionMap().put("escapeStroke", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        cancelEditing();
      }
    });
    Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
  }

  public void eventDispatched(AWTEvent event) {
    MouseEvent mouseEvent = (MouseEvent)event;
    if (mouseEvent.getClickCount() == 0 || !isShown()) {
      return;
    }
    final Component sourceComponent = mouseEvent.getComponent();
    final Point originalPoint = mouseEvent.getPoint();

    final Lookup activeLookup = LookupManager.getInstance(getEditor().getProject()).getActiveLookup();
    if (activeLookup != null){
      final DebuggerTree tree = myNode.getTree();
      final JLayeredPane layeredPane = tree.getRootPane().getLayeredPane();
      final Point layeredPoint = SwingUtilities.convertPoint(sourceComponent, originalPoint, layeredPane);
      if (activeLookup.getBounds().contains(layeredPoint)) return; //mouse click inside lookup 
    }

    final Point point = SwingUtilities.convertPoint(sourceComponent, originalPoint, myInplaceEditorComponent);
    if (myInplaceEditorComponent.contains(point)) {
      return;
    }
    final Component componentAtPoint = SwingUtilities.getDeepestComponentAt(sourceComponent, originalPoint.x, originalPoint.y);
    for (Component comp = componentAtPoint; comp != null; comp = comp.getParent()) {
      if (comp instanceof ComboPopup) {
        return;
      }
    }
    cancelEditing();
  }

  private Rectangle getEditorBounds() {
    final DebuggerTree tree = myNode.getTree();
    Rectangle bounds = tree.getVisibleRect();
    Rectangle nodeBounds = tree.getPathBounds(new TreePath(myNode.getPath()));
    bounds.y = nodeBounds.y;
    bounds.height = nodeBounds.height;

    if(nodeBounds.x > bounds.x) {
      bounds.width = bounds.width - nodeBounds.x + bounds.x;
      bounds.x = nodeBounds.x;
    }
    return bounds;
  }

  public boolean isShown() {
    return myInplaceEditorComponent != null;
  }
}
