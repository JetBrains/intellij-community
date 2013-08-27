/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.util;

import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.StructureViewComposite;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class StructureViewCompositeModel extends StructureViewModelBase {
  public StructureViewCompositeModel(PsiFile file, StructureViewComposite.StructureViewDescriptor[] views) {
    super(file, createRootNode(file, views));
  }

  private static StructureViewTreeElement createRootNode(final PsiFile file, final StructureViewComposite.StructureViewDescriptor[] views) {
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
      public ItemPresentation getPresentation() {
        return file.getPresentation();
      }

      @Override
      public TreeElement[] getChildren() {
        ArrayList<TreeElement> elements = new ArrayList<TreeElement>();
        for (StructureViewComposite.StructureViewDescriptor view : views) {
          elements.add(createTreeElementFromView(file, view));
        }
        return elements.toArray(new TreeElement[elements.size()]);
      }
    };
  }

  private static TreeElement createTreeElementFromView(final PsiFile file, final StructureViewComposite.StructureViewDescriptor view) {
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
      public ItemPresentation getPresentation() {
        return new ItemPresentation() {
          @Nullable
          @Override
          public String getPresentableText() {
            return view.title;
          }

          @Nullable
          @Override
          public String getLocationString() {
            return null;
          }

          @Nullable
          @Override
          public Icon getIcon(boolean unused) {
            return view.icon;
          }
        };
      }

      @Override
      public TreeElement[] getChildren() {
        return view.structureView.getTreeModel().getRoot().getChildren();
      }
    };
  }
}
