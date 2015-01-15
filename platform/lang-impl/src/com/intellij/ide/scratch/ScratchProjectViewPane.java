/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.scratch;

import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NavigatableWithText;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author gregsh
 */
public class ScratchProjectViewPane extends ProjectViewPane {

  public static final String ID = "Scratches";

  public ScratchProjectViewPane(Project project) {
    super(project);
  }

  @Override
  public String getTitle() {
    return "Scratches";
  }

  @Override
  public Icon getIcon() {
    return super.getIcon();
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  protected ProjectAbstractTreeStructureBase createStructure() {
    return new MyTreeStructure(myProject);
  }

  @Override
  public int getWeight() {
    return 11;
  }

  @Override
  public SelectInTarget createSelectInTarget() {
    return new ProjectViewSelectInTarget(myProject) {
      @Override
      public String toString() {
        return getTitle();
      }

      @Override
      public String getMinorViewId() {
        return getId();
      }

      @Override
      public float getWeight() {
        return ScratchProjectViewPane.this.getWeight();
      }
    };
  }

  @Nullable
  @Override
  protected PsiElement getPSIElement(@Nullable Object element) {
    return element instanceof ScratchFileService.RootType ? getDirectory(myProject, (ScratchFileService.RootType)element) : super.getPSIElement(element);
  }

  @Nullable
  private static PsiDirectory getDirectory(@NotNull Project project, @NotNull ScratchFileService.RootType rootType) {
    String path = ScratchFileService.getInstance().getRootPath(rootType);
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    return virtualFile == null ? null : PsiManager.getInstance(project).findDirectory(virtualFile);
  }

  private static class MyTreeStructure extends ProjectTreeStructure {

    MyTreeStructure(final Project project) {
      super(project, ID);
    }

    @Override
    protected AbstractTreeNode createRoot(Project project, ViewSettings settings) {
      return new MyProjectNode(project);
    }

    @Nullable
    @Override
    public List<TreeStructureProvider> getProviders() {
      return null;
    }

  }

  private static class MyProjectNode extends AbstractTreeNode<Project> {
    MyProjectNode(Project project) {
      super(project, project);
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      List<AbstractTreeNode> list = ContainerUtil.newArrayList();
      for (ScratchFileService.RootType rootType : ScratchFileService.RootType.getAllRootTypes()) {
        if (rootType.isHidden()) continue;
        list.add(new MyRootNode(getProject(), rootType));
      }
      return list;
    }

    @Override
    protected void update(PresentationData presentation) {
    }
  }

  private static class MyRootNode extends AbstractTreeNode<ScratchFileService.RootType> {

    MyRootNode(Project project, ScratchFileService.RootType type) {
      super(project, type);
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      ScratchFileService.RootType rootType = getValue();
      PsiDirectory directory = getDirectory(getProject(), rootType);
      if (directory == null) return Collections.emptyList();
      return new MyPsiNode(getProject(), directory).getChildren();
    }

    @Override
    protected void update(PresentationData presentation) {
      presentation.setIcon(PlatformIcons.DIRECTORY_CLOSED_ICON);
      presentation.setPresentableText(getValue().getDisplayName());
    }
  }

  private static class MyPsiNode extends BasePsiNode<PsiFileSystemItem> implements NavigatableWithText {

    MyPsiNode(@NotNull Project project, @NotNull PsiFileSystemItem value) {
      super(project, value, ViewSettings.DEFAULT);
    }

    @Override
    public boolean isAlwaysLeaf() {
      PsiFileSystemItem value = getValue();
      return value != null && !value.isDirectory();
    }

    @Nullable
    @Override
    protected Collection<AbstractTreeNode> getChildrenImpl() {
      if (isAlwaysLeaf()) return Collections.emptyList();
      final List<AbstractTreeNode> list = ContainerUtil.newArrayList();
      PsiFileSystemItem value = getValue();
      if (value != null) {
        value.processChildren(new PsiElementProcessor<PsiFileSystemItem>() {
          @Override
          public boolean execute(@NotNull PsiFileSystemItem element) {
            list.add(new MyPsiNode(getProject(), element));
            return true;
          }
        });
      }
      return list;
    }

    @Override
    protected void updateImpl(PresentationData data) {
      PsiFileSystemItem value = getValue();
      if (value != null) {
        data.setIcon(value.getIcon(0));
        data.setPresentableText(value.getName());
      }
    }

    @Nullable
    @Override
    public String getNavigateActionText(boolean focusEditor) {
      return null;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      PsiFileSystemItem value = getValue();
      if (!(value instanceof PsiDirectory)) return super.contains(file);
      PsiDirectory dir = (PsiDirectory)value;

      return VfsUtilCore.isAncestor(dir.getVirtualFile(), file, false) &&
             !FileTypeRegistry.getInstance().isFileIgnored(file);
    }
  }
}
