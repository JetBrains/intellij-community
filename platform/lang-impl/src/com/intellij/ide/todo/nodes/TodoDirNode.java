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
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.todo.HighlightedRegionProvider;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.todo.TodoTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.file.DirectoryIconProvider;
import com.intellij.ui.HighlightedRegion;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public final class TodoDirNode extends PsiDirectoryNode implements HighlightedRegionProvider {
  private final ArrayList<HighlightedRegion> myHighlightedRegions;
  private final TodoTreeBuilder myBuilder;


  public TodoDirNode(Project project,
                     PsiDirectory directory,
                     TodoTreeBuilder builder) {
    super(project, directory, ViewSettings.DEFAULT);
    myBuilder = builder;
    myHighlightedRegions = new ArrayList<>(2);
  }

  @Override
  public ArrayList<HighlightedRegion> getHighlightedRegions() {
    return myHighlightedRegions;
  }

  @Override
  protected void updateImpl(PresentationData data) {
    super.updateImpl(data);
    int fileCount = getFileCount(getValue());
    if (getValue() == null || !getValue().isValid() || fileCount == 0) {
      setValue(null);
      return;
    }

    VirtualFile directory = getValue().getVirtualFile();
    boolean isProjectRoot = !ProjectRootManager.getInstance(getProject()).getFileIndex().isInContent(directory);
    String newName = isProjectRoot || getStructure().getIsFlattenPackages() ? getValue().getVirtualFile().getPresentableUrl() : getValue().getName();

    int nameEndOffset = newName.length();
    int todoItemCount = getTodoItemCount(getValue());
    newName = IdeBundle.message("node.todo.group", newName, todoItemCount, fileCount);

    myHighlightedRegions.clear();

    TextAttributes textAttributes = new TextAttributes();
    Color newColor = FileStatusManager.getInstance(getProject()).getStatus(getValue().getVirtualFile()).getColor();

    if (CopyPasteManager.getInstance().isCutElement(getValue())) {
      newColor = CopyPasteManager.CUT_COLOR;
    }
    textAttributes.setForegroundColor(newColor);
    myHighlightedRegions.add(new HighlightedRegion(0, nameEndOffset, textAttributes));

    EditorColorsScheme colorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
    myHighlightedRegions.add(
      new HighlightedRegion(nameEndOffset, newName.length(), colorsScheme.getAttributes(UsageTreeColors.NUMBER_OF_USAGES)));

    data.setPresentableText(newName);
  }

  @Override
  protected void setupIcon(PresentationData data, PsiDirectory psiDirectory) {
    final VirtualFile virtualFile = psiDirectory.getVirtualFile();
    if (ProjectRootsUtil.isModuleContentRoot(virtualFile, psiDirectory.getProject())) {
      data.setIcon(new DirectoryIconProvider().getIcon(psiDirectory, 0));
    } else {
      super.setupIcon(data, psiDirectory);
    }
  }

  private TodoTreeStructure getStructure() {
    return myBuilder.getTodoTreeStructure();
  }

  @Override
  public Collection<AbstractTreeNode> getChildrenImpl() {
    return TodoTreeHelper.getInstance(getProject()).getDirectoryChildren(getValue(), myBuilder, getSettings().isFlattenPackages());
  }

  public int getFileCount(PsiDirectory directory) {
    Iterator<PsiFile> iterator = myBuilder.getFiles(directory);
    int count = 0;
    try {
      while (iterator.hasNext()) {
        PsiFile psiFile = iterator.next();
        if (getStructure().accept(psiFile)) {
          count++;
        }
      }
    }
    catch (IndexNotReadyException e) {
      return count;
    }
    return count;
  }

  public int getTodoItemCount(PsiDirectory directory) {
    if (TodoTreeHelper.getInstance(getProject()).skipDirectory(directory)) {
      return 0;
    }
    int count = 0;
    Iterator<PsiFile> iterator = myBuilder.getFiles(directory);
    while (iterator.hasNext()) {
      PsiFile psiFile = iterator.next();
      count += getStructure().getTodoItemCount(psiFile);
    }
    return count;
  }

  @Override
  public int getWeight() {
    return 2;
  }


}
