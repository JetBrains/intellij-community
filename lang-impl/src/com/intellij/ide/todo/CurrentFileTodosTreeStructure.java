package com.intellij.ide.todo;

import com.intellij.ide.todo.nodes.SingleFileToDoNode;
import com.intellij.ide.todo.nodes.ToDoRootNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

public final class CurrentFileTodosTreeStructure extends TodoTreeStructure{
  private static final Logger LOG=Logger.getInstance("#com.intellij.ide.todo.CurrentFileTodosTreeStructure");
  private static final Object[] ourEmptyArray=new Object[]{};

  /**
   * Current <code>VirtualFile</code> for which the structure is built. If <code>myFile</code> is <code>null</code>
   * then the structure is empty (contains only root node).
   */
  private PsiFile myFile;

  public CurrentFileTodosTreeStructure(Project project){
    super(project);
  }

  protected void validateCache(){
    super.validateCache();
    if(myFile!=null && !myFile.isValid()){
      VirtualFile vFile=myFile.getVirtualFile();
      if(vFile.isValid()){
        myFile=PsiManager.getInstance(myProject).findFile(vFile);
      }else{
        myFile=null;
      }
    }
  }

  PsiFile getFile(){
    return myFile;
  }

  /**
   * Sets <code>file</code> for which the structure is built. Alter this method is invoked caches should
   * be validated.
   */
  public void setFile(PsiFile file){
    myFile=file;
    myRootElement = createRootElement();
  }

  public boolean accept(PsiFile psiFile){
    if(myFile==null||!myFile.equals(psiFile)||!myFile.isValid()){
      return false;
    }
    return (myTodoFilter!=null&&myTodoFilter.accept(mySearchHelper,psiFile))||
      (myTodoFilter==null&&mySearchHelper.getTodoItemsCount(psiFile)>0);
  }

  boolean isAutoExpandNode(NodeDescriptor descriptor){
    Object element=descriptor.getElement();
    if(element==myFile){
      return true;
    }else{
      return super.isAutoExpandNode(descriptor);
    }
  }

  Object getFirstSelectableElement(){
    if (myRootElement instanceof SingleFileToDoNode){
      return ((SingleFileToDoNode)myRootElement).getFileNode();
    } else {
      return null;
    }
  }

  public boolean getIsPackagesShown() {
    return myArePackagesShown;
  }

  protected AbstractTreeNode createRootElement() {
    if  (!accept(myFile)) {
      return new ToDoRootNode(myProject, new Object(), myBuilder, mySummaryElement);
    } else {
      return new SingleFileToDoNode(myProject, myFile, myBuilder);
    }

  }
}