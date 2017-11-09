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

import com.intellij.icons.AllIcons;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.*;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectTreeBuilder;
import com.intellij.ide.projectView.impl.ProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NavigatableWithText;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author gregsh
 */
public class ScratchProjectViewPane extends ProjectViewPane {

  public static final String ID = "Scratches";

  public static boolean isScratchesMergedIntoProjectTab() {
    return (Registry.is("ide.scratch.in.project.view") || PlatformUtils.isDatabaseIDE()) &&
           !ApplicationManager.getApplication().isUnitTestMode();
  }

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

  @NotNull
  @Override
  protected BaseProjectTreeBuilder createBuilder(DefaultTreeModel treeModel) {
    ProjectTreeBuilder builder =
      new ProjectTreeBuilder(myProject, myTree, treeModel, null, (ProjectAbstractTreeStructureBase)myTreeStructure) {
        @Override
        protected ProjectViewPsiTreeChangeListener createPsiTreeChangeListener(Project project) {
          return new ProjectTreeBuilderPsiListener(project) {
            @Override
            protected void childrenChanged(PsiElement parent, boolean stopProcessingForThisModificationCount) {
              VirtualFile virtualFile = parent instanceof PsiFileSystemItem ? ((PsiFileSystemItem)parent).getVirtualFile() : null;
              if (virtualFile != null && virtualFile.isValid() && ScratchFileService.getInstance().getRootType(virtualFile) != null) {
                queueUpdateFrom(parent, true);
              }
            }
          };
        }
      };
    for (RootType rootId : RootType.getAllRootIds()) {
      if (rootId.isHidden()) continue;
      rootId.registerTreeUpdater(myProject, builder, builder::queueUpdate);
    }
    return builder;
  }

  @Override
  public SelectInTarget createSelectInTarget() {
    return new ProjectViewSelectInTarget(myProject) {

      @Override
      protected boolean canSelect(PsiFileSystemItem file) {
        VirtualFile vFile = PsiUtilCore.getVirtualFile(file);
        if (vFile == null || !vFile.isValid()) return false;
        if (!vFile.isInLocalFileSystem()) return false;

        return ScratchFileService.getInstance().getRootType(vFile) != null;
      }

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
    return element instanceof RootType ? getDirectory(myProject, (RootType)element) : super.getPSIElement(element);
  }

  @Nullable
  private static PsiDirectory getDirectory(@NotNull Project project, @NotNull RootType rootId) {
    String path = ScratchFileService.getInstance().getRootPath(rootId);
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    return virtualFile == null ? null : PsiManager.getInstance(project).findDirectory(virtualFile);
  }

  @Override
  public boolean isInitiallyVisible() {
    return !isScratchesMergedIntoProjectTab();
  }

  @NotNull
  public static AbstractTreeNode createRootNode(@NotNull Project project, @NotNull ViewSettings settings) {
    return new MyProjectNode(project, settings);
  }

  private static class MyTreeStructure extends ProjectTreeStructure {

    MyTreeStructure(final Project project) {
      super(project, ID);
    }

    @Override
    protected AbstractTreeNode createRoot(Project project, ViewSettings settings) {
      return createRootNode(project, settings);
    }

    @Nullable
    @Override
    public List<TreeStructureProvider> getProviders() {
      return null;
    }

  }

  private static class MyProjectNode extends ProjectViewNode<String> {
    MyProjectNode(Project project, ViewSettings settings) {
      super(project, ScratchesNamedScope.NAME, settings);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return file.getFileType() == ScratchFileType.INSTANCE;
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      List<AbstractTreeNode> list = ContainerUtil.newArrayList();
      for (RootType rootId : RootType.getAllRootIds()) {
        if (rootId.isHidden()) continue;
        MyRootNode e = new MyRootNode(getProject(), rootId, getSettings());
        if (e.isEmpty()) continue;
        list.add(e);
      }
      return list;
    }

    @Override
    protected void update(PresentationData presentation) {
      presentation.setPresentableText(getValue());
      presentation.setIcon(AllIcons.General.ProjectTab);
    }

    @Override
    public boolean canRepresent(Object element) {
      PsiElement item = element instanceof PsiElement ? (PsiElement)element : null;
      VirtualFile virtualFile = item == null ? null : PsiUtilCore.getVirtualFile(item);
      if (virtualFile == null) return false;
      return Comparing.equal(virtualFile.getPath(), FileUtil.toSystemIndependentName(PathManager.getScratchPath()));
    }
  }

  private static class MyRootNode extends ProjectViewNode<RootType> {

    MyRootNode(Project project, RootType type, ViewSettings settings) {
      super(project, type, settings);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return ScratchFileService.getInstance().getRootType(file) == getValue();
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      RootType rootType = getValue();
      PsiDirectory directory = getDirectory();
      if (directory == null) return Collections.emptyList();
      return new MyPsiNode(directory.getProject(), rootType, directory).getChildren();
    }

    PsiDirectory getDirectory() {
      return ScratchProjectViewPane.getDirectory(getProject(), getValue());
    }

    @Override
    protected void update(PresentationData presentation) {
      presentation.setIcon(AllIcons.Nodes.Folder);
      presentation.setPresentableText(getValue().getDisplayName());
    }

    @Override
    public boolean canRepresent(Object element) {
      return Comparing.equal(getDirectory(), element);
    }

    public boolean isEmpty() {
      PsiDirectory directory = getDirectory();
      if (directory == null) return true;
      RootType rootType = ObjectUtils.notNull(getValue());
      Project project = directory.getProject();
      return directory.processChildren(element -> rootType.isIgnored(project, element.getVirtualFile()));
    }
  }

  private static class MyPsiNode extends BasePsiNode<PsiFileSystemItem> implements NavigatableWithText {

    private final RootType myRootType;

    MyPsiNode(@NotNull Project project, RootType rootId, @NotNull PsiFileSystemItem value) {
      super(project, value, ViewSettings.DEFAULT);
      myRootType = rootId;
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
      return ReadAction.compute(() -> {
        final PsiFileSystemItem value = getValue();
        if (value == null || !value.isValid()) return Collections.emptyList();
        final List<AbstractTreeNode> list = ContainerUtil.newArrayList();
        value.processChildren(new PsiElementProcessor<PsiFileSystemItem>() {
          @Override
          public boolean execute(@NotNull PsiFileSystemItem element) {
            if (!myRootType.isIgnored(value.getProject(), element.getVirtualFile())) {
              list.add(new MyPsiNode(value.getProject(), myRootType, element));
            }
            return true;
          }
        });
        return list;
      });
    }

    @Override
    protected void updateImpl(PresentationData data) {
      PsiFileSystemItem value = getValue();
      VirtualFile virtualFile = value == null ? null : value.getVirtualFile();
      if (virtualFile != null && virtualFile.isValid()) {
        data.setIcon(value.getIcon(0));
        data.setPresentableText(ObjectUtils.chooseNotNull(myRootType.substituteName(value.getProject(), virtualFile), virtualFile.getName()));
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
