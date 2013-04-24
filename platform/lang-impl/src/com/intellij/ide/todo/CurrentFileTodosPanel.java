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

package com.intellij.ide.todo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * @author Vladimir Kondratyev
 */
abstract class CurrentFileTodosPanel extends TodoPanel{
  private static final Logger LOG=Logger.getInstance("#com.intellij.ide.todo.CurrentFileTodosPanel");

  CurrentFileTodosPanel(Project project,TodoPanelSettings settings,Content content){
    super(project,settings,true,content);
    FileEditorManager fileEditorManager=FileEditorManager.getInstance(project);
    VirtualFile[] files=fileEditorManager.getSelectedFiles();
    PsiFile psiFile = files.length == 0 ? null : PsiManager.getInstance(myProject).findFile(files[0]);
    setFile(psiFile);
    MyFileEditorManagerListener fileEditorManagerListener = new MyFileEditorManagerListener();
    // It's important to remove this listener. It prevents invocation of setFile method after the tree builder
    // is disposed.
    fileEditorManager.addFileEditorManagerListener(fileEditorManagerListener,this);
  }

  private void setFile(PsiFile file){
    // setFile method is invoked in LaterInvocator so PsiManager
    // can be already dispoded, so we need to check this before using it.
    if(isDisposed()){
      return;
    }

    if (file != null && getSelectedFile() == file) return;

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    CurrentFileTodosTreeBuilder builder = (CurrentFileTodosTreeBuilder)myTodoTreeBuilder;
    builder.setFile(file);
    if(myTodoTreeBuilder.isUpdatable()){
      Object selectableElement = builder.getTodoTreeStructure().getFirstSelectableElement();
      if(selectableElement != null){
        builder.buildNodeForElement(selectableElement);
        DefaultMutableTreeNode node = builder.getNodeForElement(selectableElement);
        LOG.assertTrue(node != null);
        myTodoTreeBuilder.getTree().getSelectionModel().setSelectionPath(new TreePath(node.getPath()));
      }
    }
  }

  private boolean isDisposed() {
    return myProject == null || PsiManager.getInstance(myProject).isDisposed();
  }

  private final class MyFileEditorManagerListener extends FileEditorManagerAdapter{
    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent e){
      VirtualFile file=e.getNewFile();
      final PsiFile psiFile=file != null && file.isValid() ? PsiManager.getInstance(myProject).findFile(file) : null;
      // This invokeLater is required. The problem is setFile does a commit to PSI, but setFile is
      // invoked inside PSI change event. It causes an Exception like "Changes to PSI are not allowed inside event processing"
      DumbService.getInstance(myProject).smartInvokeLater(new Runnable(){
            @Override
            public void run(){
              setFile(psiFile);
            }
          });
    }
  }
}
