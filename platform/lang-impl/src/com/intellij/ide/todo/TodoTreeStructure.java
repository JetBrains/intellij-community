// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoPattern;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public abstract class TodoTreeStructure extends AbstractTreeStructureBase implements ToDoSettings{
  protected TodoTreeBuilder myBuilder;
  protected AbstractTreeNode myRootElement;
  protected final ToDoSummary mySummaryElement;

  private boolean myFlattenPackages;
  protected boolean myArePackagesShown;
  private boolean myAreModulesShown;


  protected final PsiTodoSearchHelper mySearchHelper;
  /**
   * Current {@code TodoFilter}. If no filter is set then this field is {@code null}.
   */
  protected TodoFilter myTodoFilter;

  public TodoTreeStructure(Project project){
    super(project);
    myArePackagesShown=true;
    mySummaryElement=new ToDoSummary();
    mySearchHelper= PsiTodoSearchHelper.getInstance(project);
  }

  final void setTreeBuilder(TodoTreeBuilder builder){
    myBuilder=builder;
    myRootElement=createRootElement();
  }

  protected abstract AbstractTreeNode createRootElement();

  public abstract boolean accept(PsiFile psiFile);

  /**
   * Validate whole the cache
   */
  protected void validateCache(){
  }

  public final boolean isPackagesShown(){
    return myArePackagesShown;
  }

  final void setShownPackages(boolean state){
    myArePackagesShown=state;
  }

  public final boolean areFlattenPackages(){
    return myFlattenPackages;
  }

  public final void setFlattenPackages(boolean state){
    myFlattenPackages=state;
  }

  /**
   * Sets new {@code TodoFilter}. {@code null} is acceptable value. It means
   * that there is no any filtration of <code>TodoItem>/code>s.
   */
  final void setTodoFilter(TodoFilter todoFilter){
    myTodoFilter=todoFilter;
  }

  /**
   * @return first element that can be selected in the tree. The method can returns {@code null}.
   */
  abstract Object getFirstSelectableElement();

  /**
   * @return number of {@code TodoItem}s located in the file.
   */
  public final int getTodoItemCount(PsiFile psiFile){
    int count=0;
    if(psiFile != null){
      if(myTodoFilter!=null){
        for(Iterator i=myTodoFilter.iterator();i.hasNext();){
          TodoPattern pattern=(TodoPattern)i.next();
          count+=getSearchHelper().getTodoItemsCount(psiFile,pattern);
        }
      }else{
        count=getSearchHelper().getTodoItemsCount(psiFile);
      }
    }
    return count;
  }

  boolean isAutoExpandNode(NodeDescriptor descriptor){
    Object element=descriptor.getElement();
    if (element instanceof AbstractTreeNode) {
      element = ((AbstractTreeNode<?>)element).getValue();
    }
    return element == getRootElement() || element == mySummaryElement && (myAreModulesShown || myArePackagesShown);
  }

  @Override
  public final void commit() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  @Override
  public boolean hasSomethingToCommit() {
    return PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }

  @NotNull
  @Override
  public ActionCallback asyncCommit() {
    return asyncCommitDocuments(myProject);
  }

  @NotNull
  @Override
  public final Object getRootElement(){
    return myRootElement;
  }

  public boolean getIsFlattenPackages() {
    return myFlattenPackages;
  }

  public PsiTodoSearchHelper getSearchHelper() {
    return mySearchHelper;
  }

  public TodoFilter getTodoFilter() {
    return myTodoFilter;
  }

  @Override
  public List<TreeStructureProvider> getProviders() {
    return Collections.emptyList();
  }

  void setShownModules(boolean state) {
    myAreModulesShown = state;
  }

  @Override
  public boolean isModulesShown() {
    return myAreModulesShown;
  }
}