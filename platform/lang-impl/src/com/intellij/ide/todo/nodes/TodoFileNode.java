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

package com.intellij.ide.todo.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.todo.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.TodoItemImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.ui.HighlightedRegion;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class TodoFileNode extends PsiFileNode implements HighlightedRegionProvider{
  private final TodoTreeBuilder myBuilder;
  private final ArrayList<HighlightedRegion> myHighlightedRegions;
  private final boolean mySingleFileMode;

  public TodoFileNode(Project project,
                      PsiFile file,
                      TodoTreeBuilder treeBuilder,
                      boolean singleFileMode){
    super(project,file,ViewSettings.DEFAULT);
    myBuilder=treeBuilder;
    myHighlightedRegions= new ArrayList<>(2);
    mySingleFileMode=singleFileMode;
  }

  @Override
  public ArrayList<HighlightedRegion> getHighlightedRegions(){
    return myHighlightedRegions;
  }

  @Override
  public Collection<AbstractTreeNode> getChildrenImpl() {
    try {
      if (!mySingleFileMode) {
        return createGeneralList();
      } else {
        return createListForSingleFile();

      }
    }
    catch (IndexNotReadyException e) {
      return Collections.emptyList();
    }
  }

  private Collection<AbstractTreeNode> createListForSingleFile() {
    PsiFile psiFile = getValue();
    TodoItem[] items= findAllTodos(psiFile, myBuilder.getTodoTreeStructure().getSearchHelper());
    ArrayList<AbstractTreeNode> children= new ArrayList<>(items.length);
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
    if (document != null) {
      for (TodoItem todoItem : items) {
        if (todoItem.getTextRange().getEndOffset() < document.getTextLength() + 1) {
          SmartTodoItemPointer pointer = new SmartTodoItemPointer(todoItem, document);
          TodoFilter toDoFilter = getToDoFilter();
          if (toDoFilter != null) {
            TodoItemNode itemNode = new TodoItemNode(getProject(), pointer, myBuilder);
            if (toDoFilter.contains(todoItem.getPattern())) {
              children.add(itemNode);
            }
          } else {
            children.add(new TodoItemNode(getProject(), pointer, myBuilder));
          }
        }
      }
    }
    Collections.sort(children, SmartTodoItemPointerComparator.ourInstance);
    return children;
  }

  public static TodoItem[] findAllTodos(final PsiFile psiFile, final PsiTodoSearchHelper helper) {
    final List<TodoItem> todoItems = new ArrayList<>(Arrays.asList(helper.findTodoItems(psiFile)));

    psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof PsiLanguageInjectionHost) {
          InjectedLanguageUtil.enumerate(element, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
            @Override
            public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
              if (places.size() == 1) {
                Document document = PsiDocumentManager.getInstance(injectedPsi.getProject()).getCachedDocument(injectedPsi);
                if (!(document instanceof DocumentWindow)) return;
                for (TodoItem item : helper.findTodoItems(injectedPsi)) {
                  TextRange rangeInHost = ((DocumentWindow)document).injectedToHost(item.getTextRange());
                  todoItems.add(new TodoItemImpl(psiFile, rangeInHost.getStartOffset(), rangeInHost.getEndOffset(), item.getPattern()));
                }
              }
            }
          });
        }
        super.visitElement(element);
      }
    });
    return todoItems.toArray(new TodoItem[todoItems.size()]);
  }

  private Collection<AbstractTreeNode> createGeneralList() {
    ArrayList<AbstractTreeNode> children = new ArrayList<>();

    PsiFile psiFile = getValue();
    final TodoItem[] items = findAllTodos(psiFile, myBuilder.getTodoTreeStructure().getSearchHelper());
    final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);

    if (document != null) {
      for (final TodoItem todoItem : items) {
        if (todoItem.getTextRange().getEndOffset() < document.getTextLength() + 1) {
          final SmartTodoItemPointer pointer = new SmartTodoItemPointer(todoItem, document);
          TodoFilter todoFilter = getToDoFilter();
          if (todoFilter != null) {
            if (todoFilter.contains(todoItem.getPattern())) {
              children.add(new TodoItemNode(getProject(), pointer, myBuilder));
            }
          } else {
            children.add(new TodoItemNode(getProject(), pointer, myBuilder));
          }
        }
      }
    }
    Collections.sort(children, SmartTodoItemPointerComparator.ourInstance);
    return children;
  }

  private TodoFilter getToDoFilter() {
    return myBuilder.getTodoTreeStructure().getTodoFilter();
  }

  @Override
  protected void updateImpl(PresentationData data) {
    super.updateImpl(data);
    String newName;
    if(myBuilder.getTodoTreeStructure().isPackagesShown()){
      newName=getValue().getName();
    }else{
      newName=mySingleFileMode ? getValue().getName() : getValue().getVirtualFile().getPresentableUrl();
    }

    int nameEndOffset=newName.length();
    int todoItemCount;
    try {
      todoItemCount = myBuilder.getTodoTreeStructure().getTodoItemCount(getValue());
    }
    catch (IndexNotReadyException e) {
      return;
    }
    if(mySingleFileMode){
      if(todoItemCount==0){
        newName = IdeBundle.message("node.todo.no.items.found", newName);
      } else {
        newName = IdeBundle.message("node.todo.found.items", newName, todoItemCount);
      }
    }else{
      newName = IdeBundle.message("node.todo.items", newName, todoItemCount);
    }

    myHighlightedRegions.clear();

    TextAttributes textAttributes=new TextAttributes();
    textAttributes.setForegroundColor(myColor);
    myHighlightedRegions.add(new HighlightedRegion(0,nameEndOffset,textAttributes));

    EditorColorsScheme colorsScheme=UsageTreeColorsScheme.getInstance().getScheme();
    myHighlightedRegions.add(
      new HighlightedRegion(nameEndOffset,newName.length(),colorsScheme.getAttributes(UsageTreeColors.NUMBER_OF_USAGES))
    );

  }

  @Override
  public int getWeight() {
    return 4;
  }
}
