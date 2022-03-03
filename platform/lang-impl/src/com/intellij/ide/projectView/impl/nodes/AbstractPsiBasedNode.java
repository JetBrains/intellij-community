// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.projectView.impl.nodes;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.bookmark.Bookmark;
import com.intellij.ide.bookmark.BookmarkType;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewSettings;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.CompoundProjectViewNodeDecorator;
import com.intellij.ide.tags.TagManager;
import com.intellij.ide.util.treeView.AbstractTreeNode;
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
import com.intellij.pom.StatePreservingNavigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColoredText;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import static com.intellij.ide.util.treeView.NodeRenderer.getSimpleTextAttributes;

/**
 * Class for node descriptors based on PsiElements. Subclasses should define
 * method that extract PsiElement from Value.
 * @param <Value> Value of node descriptor
 */
public abstract class AbstractPsiBasedNode<Value> extends ProjectViewNode<Value> implements ValidateableNode, StatePreservingNavigatable {
  private static final Logger LOG = Logger.getInstance(AbstractPsiBasedNode.class.getName());

  protected AbstractPsiBasedNode(final Project project,
                                 @NotNull Value value,
                                final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Nullable
  protected abstract PsiElement extractPsiFromValue();

  @Nullable
  protected abstract Collection<AbstractTreeNode<?>> getChildrenImpl();

  protected abstract void updateImpl(@NotNull PresentationData data);

  @Override
  @NotNull
  public final Collection<? extends AbstractTreeNode<?>> getChildren() {
    return AstLoadingFilter.disallowTreeLoading(this::doGetChildren);
  }

  @NotNull
  private Collection<? extends AbstractTreeNode<?>> doGetChildren() {
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
  private VirtualFile getVirtualFileForValue() {
    Object value = getEqualityObject();
    if (value instanceof SmartPsiElementPointer<?>) {
      SmartPsiElementPointer<?> pointer = (SmartPsiElementPointer<?>)value;
      return pointer.getVirtualFile(); // do not retrieve PSI element
    }
    PsiElement psiElement = extractPsiFromValue();
    return PsiUtilCore.getVirtualFile(psiElement);
  }

  // Should be called in atomic action

  @Override
  public void update(@NotNull final PresentationData data) {
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

      final Icon tagIcon;
      final ColoredText tagText;
      if (!TagManager.isEnabled()) {
        tagIcon = getBookmarkIcon(myProject, value);
        tagText = null;
      }
      else {
        var tagIconAndText = TagManager.getTagIconAndText(value);
        tagIcon = tagIconAndText.first;
        tagText = tagIconAndText.second;
      }
      data.setIcon(withIconMarker(icon, tagIcon));
      data.setPresentableText(myName);
      if (deprecated) {
        data.setAttributesKey(CodeInsightColors.DEPRECATED_ATTRIBUTES);
      }
      if (tagText != null) {
        var fragments = tagText.fragments();
        for (ColoredText.Fragment fragment : fragments) {
          data.getColoredText().add(new ColoredFragment(fragment.fragmentText(), fragment.fragmentAttributes()));
        }
        if (!fragments.isEmpty()) {
          data.getColoredText().add(new ColoredFragment(myName, getSimpleTextAttributes(data)));
        }
      }
      updateImpl(data);
      data.setIcon(patchIcon(myProject, data.getIcon(true), getVirtualFile()));
      CompoundProjectViewNodeDecorator.get(myProject).decorate(this, data);
    });
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

  @Nullable
  public static Icon patchIcon(@NotNull Project project, @Nullable Icon original, @Nullable VirtualFile file) {
    if (file == null || original == null) return original;

    Icon icon = original;

    if (file.is(VFileProperty.SYMLINK)) {
      icon = LayeredIcon.create(icon, PlatformIcons.SYMLINK_ICON);
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
  public boolean contains(@NotNull final VirtualFile file) {
    return file.equals(getVirtualFileForValue());
  }

  @Nullable
  public NavigationItem getNavigationItem() {
    final PsiElement psiElement = extractPsiFromValue();
    return psiElement instanceof NavigationItem ? (NavigationItem) psiElement : null;
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

  @Nullable
  protected String calcTooltip() {
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
