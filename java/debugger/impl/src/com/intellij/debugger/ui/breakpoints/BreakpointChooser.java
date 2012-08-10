/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.util.*;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

public class BreakpointChooser {

  private DetailView myDetailView;

  private Delegate myDelegate;

  private final ComboBox myComboBox;

  private DetailController myDetailController;
  private JBList myList;

  public void setDetailView(DetailView detailView) {
    myDetailView = detailView;
    myDetailController.setDetailView(new MyDetailView(myDetailView.getEditorState()));
  }

  public Object getSelectedBreakpoint() {
    return ((BreakpointItem)myComboBox.getSelectedItem()).getBreakpoint();
  }

  private void pop(DetailView.PreviewEditorState pushed) {
    if (pushed.getFile() != null) {
      myDetailView
        .navigateInPreviewEditor(
          new DetailView.PreviewEditorState(pushed.getFile(), pushed.getNavigate(), pushed.getAttributes()));
    }
    else {
      myDetailView.clearEditor();
    }
  }
  public interface Delegate {
    void breakpointChosen(Project project, BreakpointItem breakpointItem);
  }

  public BreakpointChooser(final Project project, Delegate delegate, Breakpoint baseBreakpoint, List<BreakpointItem> breakpointItems) {
    myDelegate = delegate;

    BreakpointItem breakpointItem = null;
    for (BreakpointItem item : breakpointItems) {
      if (item.getBreakpoint() == baseBreakpoint) {
        breakpointItem = item;
        break;
      }
    }
    myDetailController = new DetailController(new MasterController() {
      JLabel fake = new JLabel();
      @Override
      public ItemWrapper[] getSelectedItems() {
        return new ItemWrapper[]{((BreakpointItem)myList.getSelectedValue())};
      }

      @Override
      public JLabel getPathLabel() {
        return fake;
      }
    });

    final ItemWrapperListRenderer listRenderer = new ItemWrapperListRenderer(project, null);

    ComboBoxModel model = new CollectionComboBoxModel(breakpointItems, breakpointItem);
    myComboBox = new ComboBox(model) {
      @Override
      protected JBList createJBList(ComboBoxModel model) {
        myList = super.createJBList(model);
        myDetailController.setList(myList);
        myList.setCellRenderer(listRenderer);
        return myList;
      }
    };
    myComboBox.setRenderer(listRenderer);

    myComboBox.setSwingPopup(false);
    myComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent event) {
        myDelegate.breakpointChosen(project, ((BreakpointItem)myComboBox.getSelectedItem()));
      }
    });
  }

  public JComponent getComponent() {
    return myComboBox;
  }

  private class MyDetailView implements DetailView {

    private final PreviewEditorState myPushed;
    private ItemWrapper myCurrentItem;

    public MyDetailView(PreviewEditorState pushed) {
      myPushed = pushed;
      putUserData(BreakpointItem.EDITOR_ONLY, Boolean.TRUE);
    }

    @Override
    public Editor getEditor() {
      return myDetailView.getEditor();
    }

    @Override
    public void navigateInPreviewEditor(PreviewEditorState editorState) {
      if (myDetailView != null) {
        myDetailView.navigateInPreviewEditor(editorState);
      }
    }

    @Override
    public JPanel getDetailPanel() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setDetailPanel(@Nullable JPanel panel) {
      //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void clearEditor() {
      pop(myPushed);
    }

    @Override
    public PreviewEditorState getEditorState() {
      return myDetailView.getEditorState();
    }

    public void setCurrentItem(ItemWrapper currentItem) {
      myCurrentItem = currentItem;
    }

    @Override
    public ItemWrapper getCurrentItem() {
      return myCurrentItem;
    }

    @Override
    public boolean hasEditorOnly() {
      return true;
    }

    UserDataHolderBase myDataHolderBase = new UserDataHolderBase();

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      return myDataHolderBase.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      myDataHolderBase.putUserData(key, value);
    }
  }
}
