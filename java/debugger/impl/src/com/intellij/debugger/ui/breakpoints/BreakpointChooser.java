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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.ui.popup.util.ItemWrapper;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.tree.BreakpointMasterDetailPopupBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public class BreakpointChooser {
  private Project myProject;

  private DetailView myDetailView;

  private ActionToolbar myActionToolbar;
  private BreakpointItem myBreakpointItem;

  private Delegate myDelegate;
  private final ComboBoxAction myComboBoxAction;
  private BreakpointMasterDetailPopupBuilder myPopupBuilder;

  private Object mySelectedBreakpoint;

  public void setDetailView(DetailView detailView) {
    myDetailView = detailView;
  }

  public Object getSelectedBreakpoint() {
    return mySelectedBreakpoint;
  }

  public BreakpointItem getBreakpointItem() {
    return myBreakpointItem;
  }

  public void setSelectedBreakpoint(Object selectedBreakpoint) {
    mySelectedBreakpoint = selectedBreakpoint;
    myBreakpointItem = selectedBreakpoint != null ? new JavaBreakpointItem(null, (Breakpoint)selectedBreakpoint) : null;
    updatePresentation(myComboBoxAction.getTemplatePresentation(), myBreakpointItem);
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
    void breakpointChosen(Project project, BreakpointItem breakpointItem, JBPopup popup);
  }

  public BreakpointChooser(Project project, Delegate delegate, Breakpoint baseBreakpoint) {
    myProject = project;
    myDelegate = delegate;

    myPopupBuilder = new BreakpointMasterDetailPopupBuilder(myProject);
    myPopupBuilder.setPlainView(true);

    myComboBoxAction = new ComboBoxAction() {

      @Override
      public void update(AnActionEvent e) {
        final Presentation presentation = e.getPresentation();
        updatePresentation(presentation, BreakpointChooser.this.myBreakpointItem);
      }

      @Override
      protected ComboBoxButton createComboBoxButton(final Presentation presentation) {
        return new ComboBoxButton(presentation) {
          @Override
          protected JBPopup createPopup(final Runnable onDispose) {
            final DetailView.PreviewEditorState pushed = myDetailView.getEditorState();
            myPopupBuilder.setIsViewer(true);
            myPopupBuilder.setAddDetailViewToEast(false);
            myPopupBuilder.setDetailView(new MyDetailView(pushed));
            myPopupBuilder.setCallback(new BreakpointMasterDetailPopupBuilder.BreakpointChosenCallback() {
              @Override
              public void breakpointChosen(Project project, BreakpointItem breakpointItem, JBPopup popup, boolean withEnterOrDoubleClick) {
                popup.cancel();
                myBreakpointItem = breakpointItem;
                mySelectedBreakpoint = breakpointItem.getBreakpoint();
                updatePresentation(myComboBoxAction.getTemplatePresentation(), myBreakpointItem);
                updatePresentation(presentation, myBreakpointItem);

                if (myDelegate != null) {
                  myDelegate.breakpointChosen(project, breakpointItem, popup);
                }
              }
            });
            myPopupBuilder.setIsViewer(true);
            JBPopup popup = myPopupBuilder.createPopup();
            popup.addListener(new JBPopupListener() {
              @Override
              public void beforeShown(LightweightWindowEvent event) {
                //To change body of implemented methods use File | Settings | File Templates.
              }

              @Override
              public void onClosed(LightweightWindowEvent event) {
                onDispose.run();
                pop(pushed);
              }
            });
            return popup;
          }


        };
      }

      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        assert false : "should not be here";
        return null;
      }
    };
    setSelectedBreakpoint(baseBreakpoint);
    myActionToolbar = ActionManager.getInstance().createActionToolbar("asdad", new DefaultActionGroup(myComboBoxAction), true);
    myActionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
  }

  public void setBreakpointItems(Collection<BreakpointItem> items) {
    myPopupBuilder.setBreakpointItems(items);
  }

  private void updatePresentation(Presentation presentation, BreakpointItem breakpointItem) {
    if (breakpointItem != null && breakpointItem.getBreakpoint() != null) {
      presentation.setIcon(breakpointItem.getIcon());
      presentation.setText(breakpointItem.getDisplayText());
    }
    else {
      presentation.setText(DebuggerBundle.message("value.none"));
    }

  }

  public JComponent getComponent() {
    return myActionToolbar.getComponent();
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
