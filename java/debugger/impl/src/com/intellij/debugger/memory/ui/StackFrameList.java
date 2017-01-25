/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

class StackFrameList extends JBList<StackFrameItem> {
  private static final char ANONYMOUS_CLASS_DELIMITER = '$';
  private static final MyOpenFilesState myEditorState = new MyOpenFilesState();

  private List<StackFrameItem> myStackFrames;
  private final Project myProject;
  private final GlobalSearchScope myScope;
  private final MyListModel myModel = new MyListModel();

  StackFrameList(@NotNull Project project,
                 @NotNull List<StackFrameItem> stack,
                 @NotNull GlobalSearchScope searchScope) {
    super();

    myStackFrames = new ArrayList<>(stack);
    myProject = project;
    myScope = searchScope;

    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    setModel(myModel);

    setCellRenderer(new ColoredListCellRenderer<StackFrameItem>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends StackFrameItem> list,
                                           StackFrameItem value, int index, boolean isSelected, boolean hasFocus) {
        value.customizePresentation(this);
        setIcon(null); // no icons in the list
      }
    });
  }

  void setFrame(@NotNull List<StackFrameItem> stack) {
    myModel.update(stack);
  }

  void navigateToSelectedValue(boolean focusOnEditor) {
    StackFrameItem selectedValue = getSelectedValue();
    if (selectedValue != null) {
      navigateToFrame(selectedValue, focusOnEditor);
    }
  }

  private void navigateToFrame(@NotNull StackFrameItem frame, boolean focusOnEditor) {
    String path = frame.path();
    int anonymousClassDelimiterIndex = path.indexOf(ANONYMOUS_CLASS_DELIMITER);
    int pathLength = anonymousClassDelimiterIndex > 0 ? anonymousClassDelimiterIndex : path.length();
    path = path.substring(0, pathLength);
    PsiClass psiClass = DebuggerUtils.findClass(path, myProject, myScope);
    if (psiClass != null) {
      ApplicationManager.getApplication().runReadAction(() -> {
        PsiElement navigationElement = psiClass.getNavigationElement();
        VirtualFile file = PsiUtilCore.getVirtualFile(navigationElement);

        if (file == null) {
          file = psiClass.getContainingFile().getVirtualFile();
        }

        OpenFileHyperlinkInfo info =
          new OpenFileHyperlinkInfo(myProject, file, frame.line() - 1);
        OpenFileDescriptor descriptor = info.getDescriptor();
        if (descriptor != null) {
          FileEditorManagerImpl manager = (FileEditorManagerImpl)FileEditorManager.getInstance(myProject);
          VirtualFile lastFile = myEditorState.myLastOpenedFile;
          if (myEditorState.myIsNeedToCloseLastOpenedFile && lastFile != null &&
              manager.isFileOpen(lastFile) && !lastFile.equals(descriptor.getFile())) {
            manager.closeFile(myEditorState.myLastOpenedFile, false, true);
          }

          descriptor.setScrollType(ScrollType.CENTER);
          descriptor.setUseCurrentWindow(true);

          if (lastFile == null || !lastFile.equals(descriptor.getFile())) {
            myEditorState.myIsNeedToCloseLastOpenedFile = !manager.isFileOpen(descriptor.getFile());
          }

          descriptor.navigateInEditor(myProject, focusOnEditor);
          FileEditor[] editors = manager.getEditors(descriptor.getFile());
          if (editors.length != 0) {
            myEditorState.myLastOpenedFile = descriptor.getFile();
          }
        }
      });
    }
  }

  private static class MyOpenFilesState {
    VirtualFile myLastOpenedFile;
    boolean myIsNeedToCloseLastOpenedFile;
  }

  private class MyListModel extends AbstractListModel<StackFrameItem> {

    void update(@NotNull List<StackFrameItem> newFrame) {
      fireIntervalRemoved(this, 0, getSize());
      myStackFrames = newFrame;
      fireIntervalAdded(this, 0, getSize());
    }

    @Override
    public int getSize() {
      return myStackFrames.size();
    }

    @Override
    public StackFrameItem getElementAt(int index) {
      return myStackFrames.get(index);
    }
  }
}
