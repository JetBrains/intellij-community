// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class MultiSelectionEventHandler extends EventHandler {
  private PluginsGroupComponent myContainer;
  private PluginsListLayout myLayout;
  private List<CellPluginComponent> myComponents;

  private CellPluginComponent myHoverComponent;

  private int mySelectionIndex;
  private int mySelectionLength;

  private final MouseAdapter myMouseHandler;
  private final KeyListener myKeyListener;
  private final FocusListener myFocusListener;

  private final ShortcutSet mySelectAllKeys;
  private final ShortcutSet myDeleteKeys;
  private boolean myAllSelected;
  private boolean myMixSelection;

  public MultiSelectionEventHandler() {
    clear();

    myMouseHandler = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        if (SwingUtilities.isLeftMouseButton(event)) {
          CellPluginComponent component = get(event);
          int index = getIndex(component);

          if (event.isShiftDown()) {
            int end = mySelectionIndex + mySelectionLength + (mySelectionLength > 0 ? -1 : 1);
            if (index != end) {
              moveOrResizeSelection(index < end, false, Math.abs(end - index));
            }
          }
          else if (event.isMetaDown()) {
            myMixSelection = true;
            myAllSelected = false;
            mySelectionIndex = index;
            mySelectionLength = 1;
            component.setSelection(component.getSelection() == SelectionType.SELECTION
                                   ? SelectionType.NONE : SelectionType.SELECTION, true);
          }
          else {
            clearSelectionWithout(index);
            singleSelection(component, index);
          }
        }
        else if (SwingUtilities.isRightMouseButton(event)) {
          CellPluginComponent component = get(event);

          if (myAllSelected || myMixSelection) {
            int size = getSelection().size();
            if (size == 0) {
              singleSelection(component, getIndex(component));
            }
            else if (size == 1) {
              ensureMoveSingleSelection(component);
            }
          }
          else if (mySelectionLength == 0 || mySelectionLength == 1) {
            ensureMoveSingleSelection(component);
          }

          DefaultActionGroup group = new DefaultActionGroup();
          component.createPopupMenu(group, getSelection());
          if (group.getChildrenCount() == 0) {
            return;
          }

          ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
          popupMenu.setTargetComponent(component);
          popupMenu.getComponent().show(event.getComponent(), event.getX(), event.getY());
          event.consume();
        }
      }

      @Override
      public void mouseExited(MouseEvent event) {
        if (myHoverComponent != null) {
          if (myHoverComponent.getSelection() == SelectionType.HOVER) {
            myHoverComponent.setSelection(SelectionType.NONE);
          }
          myHoverComponent = null;
        }
      }

      @Override
      public void mouseMoved(MouseEvent event) {
        if (myHoverComponent == null) {
          CellPluginComponent component = get(event);
          if (component.getSelection() == SelectionType.NONE) {
            myHoverComponent = component;
            component.setSelection(SelectionType.HOVER);
          }
        }
      }
    };

    mySelectAllKeys = getShortcuts(IdeActions.ACTION_SELECT_ALL);
    myDeleteKeys = getShortcuts(IdeActions.ACTION_EDITOR_DELETE);

    myKeyListener = new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent event) {
        int code = event.getKeyCode();
        int modifiers = event.getModifiers();
        KeyboardShortcut shortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(code, modifiers), null);

        if (check(shortcut, mySelectAllKeys)) {
          event.consume();
          selectAll();
          return;
        }
        if (check(shortcut, myDeleteKeys)) {
          code = DELETE_CODE;
        }

        if (code == KeyEvent.VK_HOME || code == KeyEvent.VK_END) {
          if (myComponents.isEmpty()) {
            return;
          }
          event.consume();
          if (event.isShiftDown()) {
            moveOrResizeSelection(code == KeyEvent.VK_HOME, false, 2 * myComponents.size());
          }
          else {
            int index = code == KeyEvent.VK_HOME ? 0 : myComponents.size() - 1;
            clearSelectionWithout(index);
            singleSelection(index);
          }
        }
        else if (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN) {
          event.consume();
          if (modifiers == 0) {
            moveOrResizeSelection(code == KeyEvent.VK_UP, true, 1);
          }
          else if (modifiers == Event.SHIFT_MASK) {
            moveOrResizeSelection(code == KeyEvent.VK_UP, false, 1);
          }
        }
        else if (code == KeyEvent.VK_PAGE_UP || code == KeyEvent.VK_PAGE_DOWN) {
          if (myComponents.isEmpty()) {
            return;
          }

          event.consume();
          int pageCount = myContainer.getVisibleRect().height / myLayout.myLineHeight;
          moveOrResizeSelection(code == KeyEvent.VK_PAGE_UP, !event.isShiftDown(), pageCount);
        }
        else if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER || code == DELETE_CODE) {
          assert mySelectionLength != 0;
          CellPluginComponent component = myComponents.get(mySelectionIndex);
          if (component.getSelection() != SelectionType.SELECTION) {
            component.setSelection(SelectionType.SELECTION);
          }
          component.handleKeyAction(code, getSelection());
        }
      }
    };

    myFocusListener = new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent event) {
        if (mySelectionIndex >= 0 && mySelectionLength == 1 && !myMixSelection) {
          CellPluginComponent component = get(event);
          int index = getIndex(component);
          if (mySelectionIndex != index) {
            clearSelectionWithout(index);
            singleSelection(component, index);
          }
        }
      }
    };
  }

  @Override
  public void connect(@NotNull PluginsGroupComponent container) {
    myContainer = container;
    myLayout = (PluginsListLayout)container.getLayout();
  }

  @Override
  public void addCell(@NotNull CellPluginComponent component, int index) {
    if (index == -1) {
      myComponents.add(component);
    }
    else {
      myComponents.add(index, component);
    }
  }

  @Override
  public void addCell(@NotNull CellPluginComponent component, @Nullable CellPluginComponent anchor) {
    if (anchor == null) {
      myComponents.add(component);
    }
    else {
      myComponents.add(myComponents.indexOf(anchor), component);
    }
  }

  @Override
  public void removeCell(@NotNull CellPluginComponent component) {
    myComponents.remove(component);
  }

  @Override
  public void initialSelection(boolean scrollAndFocus) {
    if (!myComponents.isEmpty() && mySelectionLength == 0) {
      singleSelection(myComponents.get(0), 0, scrollAndFocus);
    }
  }

  @Override
  @NotNull
  public List<CellPluginComponent> getSelection() {
    List<CellPluginComponent> selection = new ArrayList<>();

    for (CellPluginComponent component : myComponents) {
      if (component.getSelection() == SelectionType.SELECTION) {
        selection.add(component);
      }
    }

    return selection;
  }

  @Override
  public void clear() {
    myComponents = new ArrayList<>();
    myHoverComponent = null;
    mySelectionIndex = -1;
    mySelectionLength = 0;
    myAllSelected = false;
    myMixSelection = false;
  }

  private void selectAll() {
    if (myAllSelected) {
      return;
    }

    myAllSelected = true;
    myMixSelection = false;
    myHoverComponent = null;

    for (CellPluginComponent component : myComponents) {
      if (component.getSelection() != SelectionType.SELECTION) {
        component.setSelection(SelectionType.SELECTION, false);
      }
    }
  }

  private void moveOrResizeSelection(boolean up, boolean singleSelection, int count) {
    if (singleSelection) {
      assert mySelectionLength != 0;
      int index;
      if (mySelectionLength > 0) {
        index = up
                ? Math.max(mySelectionIndex + mySelectionLength - 1 - count, 0)
                : Math.min(mySelectionIndex + mySelectionLength - 1 + count, myComponents.size() - 1);
      }
      else {
        index = up
                ? Math.max(mySelectionIndex + mySelectionLength + 1 - count, 0)
                : Math.min(mySelectionIndex + mySelectionLength + 1 + count, myComponents.size() - 1);
      }
      clearSelectionWithout(index);
      singleSelection(index);
    }
    // multi selection
    else if (up) {
      if (mySelectionLength > 0) {
        if (mySelectionIndex + mySelectionLength - 1 > 0) {
          clearAllOrMixSelection();
          for (int i = 0; i < count && mySelectionIndex + mySelectionLength - 1 > 0; i++) {
            mySelectionLength--;
            if (mySelectionLength > 0) {
              myComponents.get(mySelectionIndex + mySelectionLength).setSelection(SelectionType.NONE, true);
            }
            if (mySelectionLength == 0) {
              myComponents.get(mySelectionIndex - 1).setSelection(SelectionType.SELECTION);
              mySelectionLength = -2;
              int newCount = count - i - 1;
              if (newCount > 0) {
                moveOrResizeSelection(true, false, newCount);
              }
              return;
            }
          }
        }
      }
      else if (mySelectionIndex + mySelectionLength + 1 > 0) {
        clearAllOrMixSelection();
        for (int i = 0, index = mySelectionIndex + mySelectionLength + 1; i < count && index > 0; i++, index--) {
          mySelectionLength--;
          myComponents.get(index - 1).setSelection(SelectionType.SELECTION);
        }
      }
    }
    // down
    else if (mySelectionLength > 0) {
      if (mySelectionIndex + mySelectionLength < myComponents.size()) {
        clearAllOrMixSelection();
        for (int i = 0, index = mySelectionIndex + mySelectionLength, size = myComponents.size();
             i < count && index < size;
             i++, index++) {
          myComponents.get(index).setSelection(SelectionType.SELECTION);
          mySelectionLength++;
        }
      }
    }
    else {
      clearAllOrMixSelection();
      for (int i = 0; i < count; i++) {
        mySelectionLength++;
        myComponents.get(mySelectionIndex + mySelectionLength).setSelection(SelectionType.NONE, true);
        if (mySelectionLength == -1) {
          mySelectionLength = 1;
          int newCount = count - i - 1;
          if (newCount > 0) {
            moveOrResizeSelection(false, false, newCount);
          }
          return;
        }
      }
    }
  }

  private int getIndex(@NotNull CellPluginComponent component) {
    int index = myComponents.indexOf(component);
    assert index >= 0 : component;
    return index;
  }

  private void clearAllOrMixSelection() {
    if (!myAllSelected && !myMixSelection) {
      return;
    }
    if (myMixSelection && mySelectionIndex != -1) {
      CellPluginComponent component = myComponents.get(mySelectionIndex);
      if (component.getSelection() != SelectionType.SELECTION) {
        component.setSelection(SelectionType.SELECTION);
      }
    }
    myAllSelected = false;
    myMixSelection = false;

    int first;
    int last;

    if (mySelectionLength > 0) {
      first = mySelectionIndex;
      last = mySelectionIndex + mySelectionLength;
    }
    else {
      first = mySelectionIndex + mySelectionLength + 1;
      last = mySelectionIndex + 1;
    }

    for (int i = 0; i < first; i++) {
      CellPluginComponent component = myComponents.get(i);
      if (component.getSelection() == SelectionType.SELECTION) {
        component.setSelection(SelectionType.NONE);
      }
    }
    for (int i = last, size = myComponents.size(); i < size; i++) {
      CellPluginComponent component = myComponents.get(i);
      if (component.getSelection() == SelectionType.SELECTION) {
        component.setSelection(SelectionType.NONE);
      }
    }
  }

  private void clearSelectionWithout(int withoutIndex) {
    myAllSelected = false;
    myMixSelection = false;
    for (int i = 0, size = myComponents.size(); i < size; i++) {
      if (i != withoutIndex) {
        CellPluginComponent component = myComponents.get(i);
        if (component.getSelection() == SelectionType.SELECTION) {
          component.setSelection(SelectionType.NONE);
        }
      }
    }
  }

  private void ensureMoveSingleSelection(CellPluginComponent component) {
    int index = getIndex(component);
    if (mySelectionLength == 0 || mySelectionIndex != index) {
      clearSelectionWithout(index);
      singleSelection(component, index);
    }
  }

  @Override
  public void setSelection(@NotNull CellPluginComponent component, boolean scrollAndFocus) {
    clearSelectionWithout(-1);
    singleSelection(component, getIndex(component), scrollAndFocus);
  }

  private void singleSelection(int index) {
    singleSelection(myComponents.get(index), index);
  }

  private void singleSelection(@NotNull CellPluginComponent component, int index) {
    singleSelection(component, index, true);
  }

  private void singleSelection(@NotNull CellPluginComponent component, int index, boolean scrollAndFocus) {
    mySelectionIndex = index;
    mySelectionLength = 1;
    if (myHoverComponent == component) {
      myHoverComponent = null;
    }
    if (component.getSelection() != SelectionType.SELECTION) {
      component.setSelection(SelectionType.SELECTION, scrollAndFocus);
    }
  }

  @Override
  public void add(@NotNull Component component) {
    component.addMouseListener(myMouseHandler);
    component.addMouseMotionListener(myMouseHandler);
    component.addKeyListener(myKeyListener);
    component.addFocusListener(myFocusListener);
  }

  @Override
  public void updateHover(@NotNull CellPluginComponent component) {
    ApplicationManager.getApplication().invokeLater(() -> {
      myHoverComponent = component;
      if (component.getSelection() == SelectionType.NONE) {
        component.setSelection(SelectionType.HOVER);
      }
    }, ModalityState.any());
  }
}