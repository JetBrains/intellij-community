// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.StructureViewComposite;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.NodeProvider;
import com.intellij.ide.util.treeView.smartTree.ProvidingTreeModel;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class StructureViewCompositeModel extends StructureViewModelBase
  implements Disposable,
             StructureViewModel.ElementInfoProvider, 
             StructureViewModel.ExpandInfoProvider {
  private final List<? extends StructureViewComposite.StructureViewDescriptor> myViews;

  public StructureViewCompositeModel(@NotNull PsiFile file,
                                     @Nullable Editor editor,
                                     @NotNull List<? extends StructureViewComposite.StructureViewDescriptor> views) {
    super(file, editor, createRootNode(file, views));
    myViews = views;
  }
  
  private @NotNull JBIterable<StructureViewModel> getModels() {
    return JBIterable.from(myViews).map(o -> o.structureModel);
  }

  @Override
  public Object getCurrentEditorElement() {
    return getModels().filterMap(o -> o.getCurrentEditorElement()).first();
  }

  private static @NotNull StructureViewTreeElement createRootNode(@NotNull PsiFile file,
                                                                  @NotNull List<? extends StructureViewComposite.StructureViewDescriptor> views) {
    JBIterable<TreeElement> children = JBIterable.from(views).map(o -> createTreeElementFromView(file, o));
    return new StructureViewTreeElement() {
      @Override
      public Object getValue() {
        return file;
      }

      @Override
      public void navigate(boolean requestFocus) {
        file.navigate(requestFocus);
      }

      @Override
      public boolean canNavigate() {
        return file.canNavigate();
      }

      @Override
      public boolean canNavigateToSource() {
        return file.canNavigateToSource();
      }

      @Override
      public @NotNull ItemPresentation getPresentation() {
        return file.getPresentation();
      }

      @Override
      public TreeElement @NotNull [] getChildren() {
        List<TreeElement> elements = children.toList();
        return elements.toArray(TreeElement.EMPTY_ARRAY);
      }
    };
  }

  @Override
  public @NotNull Collection<NodeProvider<?>> getNodeProviders() {
    return getModels().filter(ProvidingTreeModel.class).flatMap(ProvidingTreeModel::getNodeProviders).toSet();
  }

  @Override
  public Filter @NotNull [] getFilters() {
    Set<Filter> filters = getModels().flatMap(o -> JBIterable.of(o.getFilters())).toSet();
    return filters.toArray(Filter.EMPTY_ARRAY);
  }

  @Override
  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    for (ElementInfoProvider p : getModels().filter(ElementInfoProvider.class)) {
      if (p.isAlwaysShowsPlus(element)) return true;
    }
    return false;
  }

  @Override
  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    for (ElementInfoProvider p : getModels().filter(ElementInfoProvider.class)) {
      if (p.isAlwaysLeaf(element)) return true;
    }
    return false;
  }

  @Override
  public boolean isAutoExpand(@NotNull StructureViewTreeElement element) {
    if (element.getValue() instanceof StructureViewComposite.StructureViewDescriptor) return true;
    for (ExpandInfoProvider p : getModels().filter(ExpandInfoProvider.class)) {
      if (p.isAutoExpand(element)) return true;
    }
    return false;
  }

  @Override
  public boolean isSmartExpand() {
    boolean result = false;
    for (ExpandInfoProvider p : getModels().filter(ExpandInfoProvider.class)) {
      if (!p.isSmartExpand()) return false;
      result = true;
    }
    return result;
  }

  private static @NotNull TreeElement createTreeElementFromView(final PsiFile file, final StructureViewComposite.StructureViewDescriptor view) {
    return new StructureViewTreeElement() {
      @Override
      public Object getValue() {
        return view;
      }

      @Override
      public void navigate(boolean requestFocus) {
        file.navigate(requestFocus);
      }

      @Override
      public boolean canNavigate() {
        return file.canNavigate();
      }

      @Override
      public boolean canNavigateToSource() {
        return file.canNavigateToSource();
      }

      @Override
      public @NotNull ItemPresentation getPresentation() {
        return new ItemPresentation() {
          @Override
          public @Nullable String getPresentableText() {
            return view.title;
          }

          @Override
          public @Nullable Icon getIcon(boolean unused) {
            return view.icon;
          }
        };
      }

      @Override
      public TreeElement @NotNull [] getChildren() {
        return view.structureModel.getRoot().getChildren();
      }
    };
  }
}
