package com.intellij.debugger.ui.content.newUI;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.debugger.ui.content.DebuggerContentUI;
import com.intellij.debugger.ui.content.DebuggerContentUIFacade;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.*;
import com.intellij.util.ui.AwtVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class NewDebuggerContentUI implements ContentUI, DebuggerContentInfo, Disposable, DebuggerContentUIFacade {

  private ContentManager myManager;
  private MyComponent myComponent = new MyComponent();
  private GridContentContainer myGrid;
  private DebuggerSettings mySettings;

  public NewDebuggerContentUI(ActionManager actionManager, DebuggerSettings settings, String sessionName) {
    mySettings = settings;
    myGrid = new GridContentContainer(actionManager, settings, this, sessionName, settings.isToolbarHorizontal());

    myComponent.setContent(myGrid);
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void setManager(final ContentManager manager) {
    assert myManager == null;

    myManager = manager;
    myManager.addContentManagerListener(new ContentManagerListener() {
      public void contentAdded(final ContentManagerEvent event) {
        getContainer(event.getContent()).add(event.getContent(), false);
      }

      public void contentRemoved(final ContentManagerEvent event) {
        getContainer(event.getContent()).remove(event.getContent());
      }

      public void contentRemoveQuery(final ContentManagerEvent event) {
      }

      public void selectionChanged(final ContentManagerEvent event) {
      }
    });
  }

  private ContentContainer getContainer(Content content) {
    final ContentContainer.Type container = getContentState(content).getContainer();
    if (container == ContentContainer.Type.grid) {
      return myGrid;
    } else {
      throw new IllegalStateException("Unsupported content");
    }
  }

  public static NewContentState getContentState(Content content) {
    return DebuggerSettings.getInstance().getNewContentState(content);
  }

  public boolean isSingleSelection() {
    return false;
  }

  public boolean isToSelectAddedContent() {
    return false;
  }

  public void dispose() {
  }

  public void restoreLayout() {
  }

  public boolean isHorizontalToolbar() {
    return mySettings.isToolbarHorizontal();
  }

  public void setHorizontalToolbar(final boolean state) {
    mySettings.setToolbarHorizontal(state);
    myGrid.setToolbarHorizontal(state);
  }

  private class MyComponent extends Wrapper.FocusHolder implements DataProvider {
    public MyComponent() {
      setOpaque(true);
      setFocusCycleRoot(true);
    }

    @Nullable
    public Object getData(@NonNls final String dataId) {
      if (DebuggerContentUI.KEY.getName().equals(dataId)) {
        return NewDebuggerContentUI.this;
      }
      else {
        return null;
      }
    }
  }

  public static void removeScrollBorder(final Component c) {
    new AwtVisitor(c) {
      public boolean visit(final Component component) {
        if (component instanceof JScrollPane) {
          if (!hasNonPrimitiveParents(c, component)) {
            ((JScrollPane)component).setBorder(null);
          }
        }
        return false;
      }
    };
  }

  private static boolean hasNonPrimitiveParents(Component stopParent, Component c) {
    Component eachParent = c.getParent();
    while (true) {
      if (eachParent == null || eachParent == stopParent) return false;
      if (!isPrimitive(eachParent)) return true;
      eachParent = eachParent.getParent();
    }
  }

  private static boolean isPrimitive(Component c) {
    return c instanceof JPanel;
  }

  public ContentUI getContentUI() {
    return this;
  }
}
