// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.todo.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.todo.SmartTodoItemPointer;
import com.intellij.ide.todo.TodoFilter;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.TodoItemImpl;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class TodoFileNode extends PsiFileNode {
  private final TodoTreeBuilder myBuilder;
  private final boolean mySingleFileMode;

  public TodoFileNode(Project project,
                      @NotNull PsiFile file,
                      TodoTreeBuilder treeBuilder,
                      boolean singleFileMode) {
    super(project, file, ViewSettings.DEFAULT);
    myBuilder = treeBuilder;
    mySingleFileMode = singleFileMode;
  }

  @Override
  public @NotNull List<AbstractTreeNode<?>> getChildrenImpl() {
    try {
      PsiFile psiFile = getValue();
      assert psiFile != null;

      List<? extends TodoItem> items = findAllTodos(psiFile, myBuilder.getTodoTreeStructure().getSearchHelper());
      List<TodoItemNode> children = new ArrayList<>(items.size());

      Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
      if (document == null) {
        return List.of();
      }

      for (TodoItem todoItem : items) {
        if (todoItem.getTextRange().getEndOffset() > document.getTextLength()) continue;

        TodoFilter todoFilter = getToDoFilter();
        if (todoFilter != null && !todoFilter.contains(todoItem.getPattern())) continue;

        TodoItemNode node = new TodoItemNode(getProject(),
                                             new SmartTodoItemPointer(todoItem, document),
                                             myBuilder);
        children.add(node);
      }

      children.sort(Comparator.comparingInt(TodoFileNode::getOffset));
      return Collections.unmodifiableList(children);
    }
    catch (IndexNotReadyException e) {
      return List.of();
    }
  }

  public static @NotNull List<? extends TodoItem> findAllTodos(@NotNull PsiFile psiFile,
                                                               @NotNull PsiTodoSearchHelper helper) {
    List<TodoItem> todoItems = new ArrayList<>(Arrays.asList(helper.findTodoItems(psiFile)));

    psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiLanguageInjectionHost) {
          InjectedLanguageManager.getInstance(psiFile.getProject()).enumerate(element, (injectedPsi, places) -> {
            if (places.size() == 1) {
              Document document = PsiDocumentManager.getInstance(injectedPsi.getProject()).getCachedDocument(injectedPsi);
              if (!(document instanceof DocumentWindow)) return;
              for (TodoItem item : helper.findTodoItems(injectedPsi)) {
                TextRange rangeInHost = ((DocumentWindow)document).injectedToHost(item.getTextRange());
                List<TextRange> additionalRanges = ContainerUtil.map(item.getAdditionalTextRanges(),
                                                                     ((DocumentWindow)document)::injectedToHost);
                TodoItemImpl hostItem = new TodoItemImpl(psiFile, rangeInHost.getStartOffset(), rangeInHost.getEndOffset(),
                                                         item.getPattern(), additionalRanges);
                todoItems.add(hostItem);
              }
            }
          });
        }
        super.visitElement(element);
      }
    });
    return Collections.unmodifiableList(todoItems);
  }

  private TodoFilter getToDoFilter() {
    return myBuilder.getTodoTreeStructure().getTodoFilter();
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return !mySingleFileMode;
  }

  @Override
  protected void updateImpl(@NotNull PresentationData data) {
    super.updateImpl(data);
    String newName;
    if (myBuilder.getTodoTreeStructure().isPackagesShown()) {
      newName = getValue().getName();
    }
    else {
      newName = mySingleFileMode ? getValue().getName() : getValue().getVirtualFile().getPresentableUrl();
    }

    data.setPresentableText(newName);
    int todoItemCount;
    try {
      todoItemCount = myBuilder.getTodoTreeStructure().getTodoItemCount(getValue());
    }
    catch (IndexNotReadyException e) {
      return;
    }
    if (todoItemCount > 0) {
      data.setLocationString(IdeBundle.message("node.todo.items", todoItemCount));
    }
  }

  @Override
  public int getWeight() {
    return 4;
  }

  private static int getOffset(@NotNull TodoItemNode node) {
    return Objects.requireNonNull(node.getValue())
      .getTodoItem()
      .getTextRange()
      .getStartOffset();
  }
}
