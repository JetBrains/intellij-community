// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class MultiSelectionEventHandler extends EventHandler {
  private PluginsGroupComponent myContainer;
  private PagePluginLayout myLayout;
  private List<ListPluginComponent> myComponents;

  private ListPluginComponent myHoverComponent;

  private int mySelectionIndex;
  private int mySelectionLength;

  private final MouseAdapter myMouseHandler;
  private final KeyListener myKeyListener;
  private final FocusListener myFocusListener;

  private final ShortcutSet mySelectAllKeys;
  private final ShortcutSet myDeleteKeys;
  private boolean myAllSelected;
  private boolean myMixSelection;

  private Consumer<? super PluginsGroupComponent> mySelectionListener;

  public MultiSelectionEventHandler() {
    clear();

    myMouseHandler = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        boolean isLeftMouseButton = SwingUtilities.isLeftMouseButton(event);
        boolean isControlDown = event.isControlDown();

        if (!isControlDown && isLeftMouseButton) {
          ListPluginComponent component = findParent(event);
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
            fireSelectionEvent();
          }
          else {
            clearSelectionWithout(index);
            singleSelection(component, index);
          }
        }
        else if (SwingUtilities.isRightMouseButton(event) ||
                 isControlDown && isLeftMouseButton) {
          ListPluginComponent component = findParent(event);

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
          component.createPopupMenu(group, getDisableableSelection());
          if (group.getChildrenCount() == 0) {
            return;
          }

          PluginsViewCustomizerKt.getListPluginComponentCustomizer().processCreatePopupMenu(component, group, getDisableableSelection());

          ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("PluginManagerConfigurable", group);
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
          ListPluginComponent component = findParent(event);
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
      public void keyPressed(@NotNull KeyEvent event) {
        KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(event);

        if (contains(mySelectAllKeys, keyStroke)) {
          event.consume();
          selectAll();
        }
        else {
          keyPressed(event,
                     contains(myDeleteKeys, keyStroke) ? DELETE_CODE : event.getKeyCode());
        }
      }

      private void keyPressed(@NotNull KeyEvent event, int code) {
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
          Boolean singleSelection = event.getModifiersEx() == 0 ?
                                    Boolean.TRUE :
                                    event.isShiftDown() ?
                                    Boolean.FALSE :
                                    null;

          if (singleSelection != null) {
            moveOrResizeSelection(code == KeyEvent.VK_UP, singleSelection, 1);
          }
        }
        else if (code == KeyEvent.VK_PAGE_UP || code == KeyEvent.VK_PAGE_DOWN) {
          if (myComponents.isEmpty()) {
            return;
          }

          event.consume();
          int pageCount = myLayout.getPageCount(myContainer);
          moveOrResizeSelection(code == KeyEvent.VK_PAGE_UP, !event.isShiftDown(), pageCount);
        }
        else if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER || code == DELETE_CODE) {
          assert mySelectionLength != 0;
          ListPluginComponent component = myComponents.get(mySelectionIndex);
          if (component.getSelection() != SelectionType.SELECTION) {
            component.setSelection(SelectionType.SELECTION);
          }
          component.handleKeyAction(event, getDisableableSelection());

          PluginsViewCustomizerKt.getListPluginComponentCustomizer().processHandleKeyAction(component, event, getDisableableSelection());
        }
      }

      private static boolean contains(@Nullable ShortcutSet shortcutSet, @NotNull KeyStroke keyStroke) {
        for (Shortcut shortcut : shortcutSet != null ? shortcutSet.getShortcuts() : Shortcut.EMPTY_ARRAY) {
          if (shortcut instanceof KeyboardShortcut &&
              ((KeyboardShortcut)shortcut).getFirstKeyStroke().equals(keyStroke)) {
            return true;
          }
        }
        return false;
      }
    };

    myFocusListener = new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent event) {
        if (mySelectionIndex >= 0 && mySelectionLength == 1 && !myMixSelection) {
          ListPluginComponent component = findParent(event);
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
  public void handleUpDown(@NotNull KeyEvent event) {
    if (myComponents.isEmpty()) {
      return;
    }

    myComponents.get(0).onSelection(() -> myKeyListener.keyPressed(event));
  }

  @Override
  public void connect(@NotNull PluginsGroupComponent container) {
    myContainer = container;
    myLayout = (PagePluginLayout)container.getLayout();
  }

  @Override
  public void addCell(@NotNull ListPluginComponent component, int index) {
    if (index == -1) {
      myComponents.add(component);
    }
    else {
      myComponents.add(index, component);
    }
  }

  @Override
  public void addCell(@NotNull ListPluginComponent component, @Nullable ListPluginComponent anchor) {
    if (anchor == null) {
      myComponents.add(component);
    }
    else {
      myComponents.add(myComponents.indexOf(anchor), component);
    }
  }

  @Override
  public void removeCell(@NotNull ListPluginComponent component) {
    myComponents.remove(component);
  }

  @Override
  public int getCellIndex(@NotNull ListPluginComponent component) {
    return myComponents.indexOf(component);
  }

  @Override
  public void initialSelection(boolean scrollAndFocus) {
    if (myComponents.isEmpty() || mySelectionLength != 0) {
      return;
    }

    ListPluginComponent component = myComponents.get(0);
    component.onSelection(() -> singleSelection(component, 0, scrollAndFocus));
  }

  @Override
  public void setSelectionListener(@Nullable Consumer<? super PluginsGroupComponent> listener) {
    mySelectionListener = listener;
  }

  private void fireSelectionEvent() {
    if (mySelectionListener != null) {
      mySelectionListener.accept(myContainer);
    }
  }

  @Override
  public @NotNull List<ListPluginComponent> getSelection() {
    return myComponents.stream().filter(component -> component.getSelection() == SelectionType.SELECTION).toList();
  }

  private @NotNull List<ListPluginComponent> getDisableableSelection() {
    return getSelection().stream().filter(pluginComponent -> pluginComponent.isDisableAllowed()).toList();
  }

  @Override
  public void updateSelection() {
    if (myComponents.isEmpty()) {
      clear();
    }
    else {
      List<Integer> selection = new ArrayList<>();
      for (int i = 0, size = myComponents.size(); i < size && selection.size() < 2; i++) {
        if (myComponents.get(i).getSelection() == SelectionType.SELECTION) {
          selection.add(i);
        }
      }

      mySelectionIndex = -1;
      mySelectionLength = 0;
      myAllSelected = false;
      myMixSelection = false;

      int size = selection.size();

      if (size > 0) {
        mySelectionIndex = selection.get(0);
        mySelectionLength = 1;
        myMixSelection = size > 1;
      }

      fireSelectionEvent();
    }
  }

  @Override
  public void clear() {
    myComponents = new ArrayList<>();
    myHoverComponent = null;
    mySelectionIndex = -1;
    mySelectionLength = 0;
    myAllSelected = false;
    myMixSelection = false;
    fireSelectionEvent();
  }

  private void selectAll() {
    if (myAllSelected) {
      return;
    }

    myAllSelected = true;
    myMixSelection = false;
    myHoverComponent = null;

    for (ListPluginComponent component : myComponents) {
      if (component.getSelection() != SelectionType.SELECTION) {
        component.setSelection(SelectionType.SELECTION, false);
      }
    }

    fireSelectionEvent();
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
              fireSelectionEvent();
              return;
            }
          }
          fireSelectionEvent();
        }
      }
      else if (mySelectionIndex + mySelectionLength + 1 > 0) {
        clearAllOrMixSelection();
        for (int i = 0, index = mySelectionIndex + mySelectionLength + 1; i < count && index > 0; i++, index--) {
          mySelectionLength--;
          myComponents.get(index - 1).setSelection(SelectionType.SELECTION);
        }
        fireSelectionEvent();
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
        fireSelectionEvent();
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
          fireSelectionEvent();
          return;
        }
      }
      fireSelectionEvent();
    }
  }

  private int getIndex(@NotNull ListPluginComponent component) {
    int index = myComponents.indexOf(component);
    assert index >= 0 : component;
    return index;
  }

  private void clearAllOrMixSelection() {
    if (!myAllSelected && !myMixSelection) {
      return;
    }
    if (myMixSelection && mySelectionIndex != -1) {
      ListPluginComponent component = myComponents.get(mySelectionIndex);
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
      ListPluginComponent component = myComponents.get(i);
      if (component.getSelection() == SelectionType.SELECTION) {
        component.setSelection(SelectionType.NONE);
      }
    }
    for (int i = last, size = myComponents.size(); i < size; i++) {
      ListPluginComponent component = myComponents.get(i);
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
        ListPluginComponent component = myComponents.get(i);
        if (component.getSelection() == SelectionType.SELECTION) {
          component.setSelection(SelectionType.NONE);
        }
      }
    }
  }

  private void ensureMoveSingleSelection(ListPluginComponent component) {
    int index = getIndex(component);
    if (mySelectionLength == 0 || mySelectionIndex != index) {
      clearSelectionWithout(index);
      singleSelection(component, index);
    }
  }

  @Override
  public void setSelection(@NotNull ListPluginComponent component, boolean scrollAndFocus) {
    clearSelectionWithout(-1);
    singleSelection(component, getIndex(component), scrollAndFocus);
  }

  @Override
  public void setSelection(@NotNull List<ListPluginComponent> components) {
    clearSelectionWithout(-1);
    mySelectionIndex = -1;
    mySelectionLength = components.size();

    if (mySelectionLength == 0) {
      return;
    }

    mySelectionIndex = getIndex(components.get(0));

    for (ListPluginComponent component : components) {
      mySelectionIndex = Math.min(mySelectionIndex, getIndex(component));
      if (component.getSelection() != SelectionType.SELECTION) {
        component.setSelection(SelectionType.SELECTION, true);
      }
    }

    fireSelectionEvent();
  }

  private void singleSelection(int index) {
    singleSelection(myComponents.get(index), index);
  }

  private void singleSelection(@NotNull ListPluginComponent component, int index) {
    singleSelection(component, index, true);
  }

  private void singleSelection(@NotNull ListPluginComponent component, int index, boolean scrollAndFocus) {
    mySelectionIndex = index;
    mySelectionLength = 1;
    if (myHoverComponent == component) {
      myHoverComponent = null;
    }
    if (component.getSelection() != SelectionType.SELECTION) {
      component.setSelection(SelectionType.SELECTION, scrollAndFocus);
    }
    fireSelectionEvent();
  }

  @Override
  public void add(@NotNull Component component) {
    component.addMouseListener(myMouseHandler);
    component.addMouseMotionListener(myMouseHandler);
    component.addKeyListener(myKeyListener);
    component.addFocusListener(myFocusListener);
  }

  @Override
  public void updateHover(@NotNull ListPluginComponent component) {
    ApplicationManager.getApplication().invokeLater(() -> {
      myHoverComponent = component;
      if (component.getSelection() == SelectionType.NONE) {
        component.setSelection(SelectionType.HOVER);
      }
    }, ModalityState.any());
  }

  private static @NotNull ListPluginComponent findParent(@NotNull ComponentEvent event) {
    return Objects.requireNonNull(ComponentUtil.getParentOfType(ListPluginComponent.class, event.getComponent()));
  }
}