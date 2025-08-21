// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.bookmark.Bookmark;
import com.intellij.ide.bookmark.BookmarkType;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.projectView.*;
import com.intellij.ide.projectView.impl.CompoundProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.ProjectViewInplaceCommentProducerImplKt;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.InplaceCommentAppender;
import com.intellij.ide.util.treeView.ValidateableNode;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.navigation.NavigationRequest;
import com.intellij.pom.StatePreservingNavigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.icons.PredefinedIconOverlayService;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.IconUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import static com.intellij.ide.projectView.impl.ProjectViewUtilKt.getFileTimestamp;
import static com.intellij.ide.projectView.impl.nodes.ProjectViewNodeExtensionsKt.getVirtualFileForNodeOrItsPSI;

/**
 * Class for node descriptors based on PsiElements. Subclasses should define a method that extracts PsiElement from Value.
 * @param <Value> Value of node descriptor
 */
public abstract class AbstractPsiBasedNode<Value> extends ProjectViewNode<Value> implements ValidateableNode, StatePreservingNavigatable {
  private static final Logger LOG = Logger.getInstance(AbstractPsiBasedNode.class.getName());
  private volatile long timestamp;

  protected AbstractPsiBasedNode(final Project project,
                                 @NotNull Value value,
                                final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  protected abstract @Nullable PsiElement extractPsiFromValue();

  protected abstract @Nullable Collection<AbstractTreeNode<?>> getChildrenImpl();

  protected abstract void updateImpl(@NotNull PresentationData data);

  @ApiStatus.Internal
  @Override
  protected @Nullable VirtualFile getCacheableFile() {
    var file = super.getCacheableFile();
    return file != null ? file : getVirtualFileForValue();
  }

  @Override
  public final @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
    return AstLoadingFilter.disallowTreeLoading(this::doGetChildren);
  }

  private @NotNull Collection<? extends AbstractTreeNode<?>> doGetChildren() {
    final PsiElement psiElement = extractPsiFromValue();
    if (psiElement == null) {
      return new ArrayList<>();
    }
    if (!psiElement.isValid()) {
      LOG.error(new IllegalStateException("Node contains invalid PSI: "
                                          + "\n" + getClass() + " [" + this + "]"
                                          + "\n" + psiElement.getClass() + " [" + psiElement + "]"));
      return Collections.emptyList();
    }

    Collection<AbstractTreeNode<?>> children = getChildrenImpl();
    return children != null ? children : Collections.emptyList();
  }

  @Override
  public boolean isValid() {
    final PsiElement psiElement = extractPsiFromValue();
    return psiElement != null && psiElement.isValid();
  }

  protected boolean isMarkReadOnly() {
    final AbstractTreeNode<?> parent = getParent();
    if (parent == null) {
      return false;
    }
    if (parent instanceof AbstractPsiBasedNode) {
      final PsiElement psiElement = ((AbstractPsiBasedNode<?>)parent).extractPsiFromValue();
      return psiElement instanceof PsiDirectory;
    }

    final Object parentValue = parent.getValue();
    return parentValue instanceof PsiDirectory || parentValue instanceof Module;
  }


  @Override
  public FileStatus getFileStatus() {
    return computeFileStatus(getVirtualFileForValue(), Objects.requireNonNull(getProject()));
  }

  protected static FileStatus computeFileStatus(@Nullable VirtualFile virtualFile, @NotNull Project project) {
    if (virtualFile == null) {
      return FileStatus.NOT_CHANGED;
    }
    return FileStatusManager.getInstance(project).getStatus(virtualFile);
  }

  @Nullable
  VirtualFile getVirtualFileForValue() {
    Object value = getEqualityObject();
    if (value instanceof SmartPsiElementPointer<?> pointer) {
      return pointer.getVirtualFile(); // do not retrieve PSI element
    }
    PsiElement psiElement = extractPsiFromValue();
    return PsiUtilCore.getVirtualFile(psiElement);
  }

  @Override
  public @Nullable Comparable<?> getTimeSortKey() {
    return timestamp == 0 ? null : timestamp;
  }

  @Override
  protected void appendInplaceComments(@NotNull InplaceCommentAppender appender) {
    if (UISettings.getInstance().getShowInplaceComments()) {
      ProjectViewInplaceCommentProducerImplKt.appendInplaceComments(this, appender);
    }
    ProjectViewInplaceCommentProducerImplKt.appendVfsInfo(this, appender);
  }

  // Should be called in atomic action

  @Override
  public void update(final @NotNull PresentationData data) {
    AstLoadingFilter.disallowTreeLoading(() -> doUpdate(data));
  }

  private void doUpdate(@NotNull PresentationData data) {
    ApplicationManager.getApplication().runReadAction(() -> {
      if (!validate()) {
        return;
      }

      final PsiElement value = extractPsiFromValue();
      LOG.assertTrue(value.isValid());

      int flags = getIconableFlags();
      Icon icon = null;
      boolean deprecated = false;
      try {
        icon = value.getIcon(flags);
      }
      catch (IndexNotReadyException ignored) {
      }
      try {
        deprecated = isDeprecated();
      }
      catch (IndexNotReadyException ignored) {
      }

      final Icon tagIcon = getBookmarkIcon(myProject, value);
      data.setIcon(withIconMarker(icon, tagIcon));
      data.setPresentableText(myName);
      if (deprecated) {
        data.setAttributesKey(CodeInsightColors.DEPRECATED_ATTRIBUTES);
      }
      updateImpl(data);
      data.setIcon(patchIcon(myProject, data.getIcon(true), getVirtualFile()));
      CompoundProjectViewNodeDecorator.get(myProject).decorate(this, data);
      updateTimestamp();
    });
  }

  private void updateTimestamp() {
    if (
      getSettings() instanceof ProjectViewSettings projectViewSettings &&
      projectViewSettings.getSortKey() != NodeSortKey.BY_TIME_DESCENDING &&
      projectViewSettings.getSortKey() != NodeSortKey.BY_TIME_ASCENDING
    ) {
      timestamp = 0; // skip for performance reasons
      return;
    }
    var timestamp = getFileTimestamp(getVirtualFileForNodeOrItsPSI(this));
    this.timestamp = timestamp == null ? 0 : timestamp;
  }

  @Iconable.IconFlags
  protected int getIconableFlags() {
    int flags = 0;
    ViewSettings settings = getSettings();
    if (settings instanceof ProjectViewSettings && ((ProjectViewSettings)settings).isShowVisibilityIcons()) {
      flags |= Iconable.ICON_FLAG_VISIBILITY;
    }
    if (isMarkReadOnly()) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }
    return flags;
  }

  public static @Nullable Icon patchIcon(@NotNull Project project, @Nullable Icon original, @Nullable VirtualFile file) {
    if (file == null || original == null) return original;

    Icon icon = original;

    if (file.is(VFileProperty.SYMLINK)) {
      PredefinedIconOverlayService iconOverlayService = PredefinedIconOverlayService.Companion.getInstanceOrNull();
      if (iconOverlayService != null) {
        icon = iconOverlayService.createSymlinkIcon(icon);
      }
    }

    Icon bookmarkIcon = getBookmarkIcon(project, file);
    if (bookmarkIcon != null) {
      icon = withIconMarker(icon, bookmarkIcon);
    }

    return icon;
  }

  private static @Nullable Icon withIconMarker(@Nullable Icon icon, @Nullable Icon marker) {
    return Registry.is("ide.project.view.bookmarks.icon.before", false)
           ? IconUtil.rowIcon(marker, icon)
           : IconUtil.rowIcon(icon, marker);
  }

  private static @Nullable Icon getBookmarkIcon(@NotNull Project project, @Nullable Object context) {
    if (Registry.is("ide.project.view.bookmarks.icon.hide", false)) return null;
    BookmarksManager manager = BookmarksManager.getInstance(project);
    if (manager == null) return null; // bookmarks manager is not available
    Bookmark bookmark = manager.createBookmark(context);
    if (bookmark == null) return null; // bookmark cannot be created
    BookmarkType type = manager.getType(bookmark);
    if (type == null) return null; // bookmark is not set
    return type.getIcon();
  }

  protected boolean isDeprecated() {
    return false;
  }

  @Override
  public boolean contains(final @NotNull VirtualFile file) {
    return file.equals(getVirtualFileForValue());
  }

  public @Nullable NavigationItem getNavigationItem() {
    final PsiElement psiElement = extractPsiFromValue();
    return psiElement instanceof NavigationItem ? (NavigationItem) psiElement : null;
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  @Override
  public @Nullable NavigationRequest navigationRequest() {
    if (ReflectionUtil.getMethodDeclaringClass(getClass(), "navigate", boolean.class) != AbstractPsiBasedNode.class) {
      return super.navigationRequest(); // raw
    }
    PsiElement element = extractPsiFromValue();
    if (element == null) {
      return null;
    }
    return ((NavigationItem)element).navigationRequest();
  }

  @Override
  public void navigate(boolean requestFocus, boolean preserveState) {
    if (canNavigate()) {
      if (requestFocus || preserveState) {
        NavigationUtil.openFileWithPsiElement(extractPsiFromValue(), requestFocus, requestFocus);
      }
      else {
        getNavigationItem().navigate(false);
      }
    }
  }

  @Override
  public void navigate(boolean requestFocus) {
    navigate(requestFocus, false);
  }

  @Override
  public boolean canNavigate() {
    final NavigationItem item = getNavigationItem();
    return item != null && item.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    final NavigationItem item = getNavigationItem();
    return item != null && item.canNavigateToSource();
  }

  protected @Nullable String calcTooltip() {
    return null;
  }

  @Override
  public boolean validate() {
    final PsiElement psiElement = extractPsiFromValue();
    if (psiElement == null || !psiElement.isValid()) {
      setValue(null);
    }

    return getValue() != null;
  }
}
