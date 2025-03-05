// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.libraries.ui.OrderRootTypePresentation;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class LibraryTreeStructure extends AbstractTreeStructure {
  private final NodeDescriptor myRootElementDescriptor;
  private final LibraryRootsComponent myParentEditor;
  private final LibraryRootsComponentDescriptor myComponentDescriptor;

  LibraryTreeStructure(LibraryRootsComponent parentElement, LibraryRootsComponentDescriptor componentDescriptor) {
    myParentEditor = parentElement;
    myComponentDescriptor = componentDescriptor;
    myRootElementDescriptor = new NodeDescriptor(null, null) {
      @Override
      public boolean update() {
        myName = JavaUiBundle.message("library.root.node");
        return false;
      }
      @Override
      public Object getElement() {
        return this;
      }
    };
  }

  @Override
  public @NotNull Object getRootElement() {
    return myRootElementDescriptor;
  }

  @Override
  public Object @NotNull [] getChildElements(@NotNull Object element) {
    final LibraryEditor libraryEditor = myParentEditor.getLibraryEditor();
    if (element == myRootElementDescriptor) {
      List<LibraryTableTreeContentElement> elements = new ArrayList<>(3);
      for (OrderRootType type : myComponentDescriptor.getRootTypes()) {
        final String[] urls = libraryEditor.getUrls(type);
        if (urls.length > 0) {
          OrderRootTypePresentation presentation = myComponentDescriptor.getRootTypePresentation(type);
          if (presentation == null) {
            presentation = DefaultLibraryRootsComponentDescriptor.getDefaultPresentation(type);
          }
          elements.add(new OrderRootTypeElement(myRootElementDescriptor, type, presentation.getNodeText(), presentation.getIcon()));
        }
      }
      return elements.toArray();
    }

    if (element instanceof OrderRootTypeElement rootTypeElement) {
      OrderRootType orderRootType = rootTypeElement.getOrderRootType();
      final String[] urls = libraryEditor.getUrls(orderRootType).clone();
      Arrays.sort(urls, LibraryRootsComponent.ourUrlComparator);
      List<ItemElement> items = new ArrayList<>(urls.length);
      for (String url : urls) {
        items.add(new ItemElement(rootTypeElement, url, orderRootType, libraryEditor.isJarDirectory(url, orderRootType), libraryEditor.isValid(url, orderRootType)));
      }
      return items.toArray();
    }

    if (element instanceof ItemElement itemElement) {
      List<String> excludedUrls = new ArrayList<>();
      for (String excludedUrl : libraryEditor.getExcludedRootUrls()) {
        if (VfsUtilCore.isEqualOrAncestor(itemElement.getUrl(), excludedUrl)) {
          excludedUrls.add(excludedUrl);
        }
      }
      ExcludedRootElement[] items = new ExcludedRootElement[excludedUrls.size()];
      excludedUrls.sort(LibraryRootsComponent.ourUrlComparator);
      for (int i = 0; i < excludedUrls.size(); i++) {
        items[i] = new ExcludedRootElement(itemElement, itemElement.getUrl(), excludedUrls.get(i));
      }
      return items;
    }
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public Object getParentElement(@NotNull Object element) {
    return ((NodeDescriptor<?>)element).getParentDescriptor();
  }

  @Override
  public @NotNull NodeDescriptor createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
    return (NodeDescriptor)element;
  }
}
