package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author Vladimir Kondratyev
 */
public class CurrentFileTodosTreeBuilder extends TodoTreeBuilder{
  public CurrentFileTodosTreeBuilder(JTree tree,DefaultTreeModel treeModel,Project project){
    super(tree,treeModel,project);
  }

  protected TodoTreeStructure createTreeStructure(){
    return new CurrentFileTodosTreeStructure(myProject);
  }

  void rebuildCache(){
    myFileTree.clear();
    myDirtyFileSet.clear();
    myFile2Highlighter.clear();

    CurrentFileTodosTreeStructure treeStructure=(CurrentFileTodosTreeStructure)getTreeStructure();
    PsiFile psiFile=treeStructure.getFile();
    if(treeStructure.accept(psiFile)){
      myFileTree.add(psiFile.getVirtualFile());
    }

    treeStructure.validateCache();
  }

  /**
   * @see com.intellij.ide.todo.CurrentFileTodosTreeStructure#setFile
   */
  public void setFile(PsiFile file){
    CurrentFileTodosTreeStructure treeStructure=(CurrentFileTodosTreeStructure)getTreeStructure();
    treeStructure.setFile(file);
    rebuildCache();
    updateTree(false);
  }
}
