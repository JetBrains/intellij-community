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
import com.intellij.ide.todo.HighlightedRegionProvider;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.todo.TodoTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.HighlightedRegion;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class ModuleToDoNode extends BaseToDoNode<Module> implements HighlightedRegionProvider {
  private final ArrayList<HighlightedRegion> myHighlightedRegions;

  public ModuleToDoNode(Project project, Module value, TodoTreeBuilder builder) {
    super(project, value, builder);
    myHighlightedRegions = new ArrayList<HighlightedRegion>(2);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    ArrayList<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    if (myToDoSettings.getIsPackagesShown()) {
      TodoTreeHelper.getInstance(getProject()).addPackagesToChildren(children, getValue(), myBuilder);
    }
    else {
      for (Iterator i = myBuilder.getAllFiles(); i.hasNext();) {
        final PsiFile psiFile = (PsiFile)i.next();
        if (psiFile == null) { // skip invalid PSI files
          continue;
        }
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        final boolean isInContent = ModuleRootManager.getInstance(getValue()).getFileIndex().isInContent(virtualFile);
        if (!isInContent) continue;
        TodoFileNode fileNode = new TodoFileNode(getProject(), psiFile, myBuilder, false);
        if (getTreeStructure().accept(psiFile) && !children.contains(fileNode)) {
          children.add(fileNode);
        }
      }
    }
    return children;

  }

  private TodoTreeStructure getStructure() {
    return myBuilder.getTodoTreeStructure();
  }

  @Override
  public void update(PresentationData presentation) {
    String newName = getValue().getName();
    int nameEndOffset = newName.length();
    int todoItemCount = getTodoItemCount(getValue());
    int fileCount = getFileCount(getValue());
    newName = IdeBundle.message("node.todo.group", newName, todoItemCount, fileCount);
    myHighlightedRegions.clear();

    TextAttributes textAttributes = new TextAttributes();

    if (CopyPasteManager.getInstance().isCutElement(getValue())) {
      textAttributes.setForegroundColor(CopyPasteManager.CUT_COLOR);
    }
    myHighlightedRegions.add(new HighlightedRegion(0, nameEndOffset, textAttributes));

    EditorColorsScheme colorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
    myHighlightedRegions.add(
      new HighlightedRegion(nameEndOffset, newName.length(), colorsScheme.getAttributes(UsageTreeColors.NUMBER_OF_USAGES)));
    presentation.setIcon(ModuleType.get(getValue()).getIcon());
    presentation.setPresentableText(newName);
  }

  @Override
  public String getTestPresentation() {
    return "Module";
  }

  @Override
  public ArrayList<HighlightedRegion> getHighlightedRegions() {
    return myHighlightedRegions;
  }

  @Override
  public int getFileCount(Module module) {
    Iterator<PsiFile> iterator = myBuilder.getFiles(module);
    int count = 0;
    while (iterator.hasNext()) {
      PsiFile psiFile = iterator.next();
      if (getStructure().accept(psiFile)) {
        count++;
      }
    }
    return count;
  }

  @Override
  public int getTodoItemCount(final Module val) {
    Iterator<PsiFile> iterator = myBuilder.getFiles(val);
    int count = 0;
    while (iterator.hasNext()) {
      final PsiFile psiFile = iterator.next();
      count += ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {
        @Override
        public Integer compute() {
          return getTreeStructure().getTodoItemCount(psiFile);
        }
      });
    }
    return count;
  }

  @Override
  public int getWeight() {
    return 1;
  }
}
