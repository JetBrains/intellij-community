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
package com.intellij.ide.actions;

import com.intellij.ide.util.PlatformModuleRendererFactory;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.FilePathSplittingPolicy;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.LinkedList;

/**
* @author Konstantin Bulenkov
*/
class SearchEverywherePsiRenderer extends PsiElementListCellRenderer<PsiElement> {

  private final JList myList;

  public SearchEverywherePsiRenderer(JList list) {
    myList = list;
    setFocusBorderEnabled(false);
    setLayout(new BorderLayout() {
      @Override
      public void layoutContainer(Container target) {
        super.layoutContainer(target);
        final Component right = getLayoutComponent(EAST);
        final Component left = getLayoutComponent(WEST);

        //IDEA-140824
        if (right != null && left != null && left.getBounds().x + left.getBounds().width > right.getBounds().x) {
          final Rectangle bounds = right.getBounds();
          final int newX = left.getBounds().x + left.getBounds().width;
          right.setBounds(newX, bounds.y, bounds.width - (newX - bounds.x), bounds.height);
        }
      }
    });
  }

  @Override
  public String getElementText(PsiElement element) {
    final String name = element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : null;
    return StringUtil.notNullize(name, "<unnamed>");
  }

  @Override
  protected String getContainerText(PsiElement element, String name) {
    if (element instanceof PsiFileSystemItem) {
      PsiFileSystemItem parent = ((PsiFileSystemItem)element).getParent();
      final PsiDirectory psiDirectory = parent instanceof PsiDirectory ? (PsiDirectory)parent : null;
      VirtualFile virtualFile = psiDirectory == null ? null : psiDirectory.getVirtualFile();
      if (virtualFile == null) return null;
      final String relativePath = getRelativePath(virtualFile, element.getProject());
      if (relativePath == null) return "( " + File.separator + " )";
      int width = myList.getWidth();
      if (width == 0) width += 800;
      String path = FilePathSplittingPolicy.SPLIT_BY_SEPARATOR.getOptimalTextForComponent(name, new File(relativePath), this, width - myRightComponentWidth - 16 - 10);
      return "(" + path + ")";
    }
    return getSymbolContainerText(name, element);
  }

  private String getSymbolContainerText(String name, PsiElement element) {
    String text = SymbolPresentationUtil.getSymbolContainerText(element);

    if (myList.getWidth() == 0) return text;
    if (text == null) return null;

    if (text.startsWith("(") && text.endsWith(")")) {
      text = text.substring(1, text.length()-1);
    }
    boolean in = text.startsWith("in ");
    if (in) text = text.substring(3);
    final FontMetrics fm = myList.getFontMetrics(myList.getFont());
    final int maxWidth = myList.getWidth() - fm.stringWidth(name) - 16 - myRightComponentWidth - 20;
    String left = in ? "(in " : "(";
    String right = ")";

    if (fm.stringWidth(left + text + right) < maxWidth) return left + text + right;
    final LinkedList<String> parts = new LinkedList<>(StringUtil.split(text, "."));
    int index;
    while (parts.size() > 1) {
      index = parts.size() / 2 - 1;
      parts.remove(index);
      if (fm.stringWidth(StringUtil.join(parts, ".") + "...") < maxWidth) {
        parts.add(index, index == 0 ? "..." : ".");
        return left + StringUtil.join(parts, ".") + right;
      }
    }
    //todo
    return left + "..." + right;
  }


  @Nullable
  String getRelativePath(final VirtualFile virtualFile, final Project project) {
    String url = virtualFile.getPresentableUrl();
    if (project == null) {
      return url;
    }
    VirtualFile root = ProjectFileIndex.SERVICE.getInstance(project).getContentRootForFile(virtualFile);
    if (root != null) {
      return root.getName() + File.separatorChar + VfsUtilCore.getRelativePath(virtualFile, root, File.separatorChar);
    }

    final VirtualFile baseDir = project.getBaseDir();
    if (baseDir != null) {
      //noinspection ConstantConditions
      final String projectHomeUrl = baseDir.getPresentableUrl();
      if (url.startsWith(projectHomeUrl)) {
        final String cont = url.substring(projectHomeUrl.length());
        if (cont.isEmpty()) return null;
        url = "..." + cont;
      }
    }
    return url;
  }

  @Override
  protected boolean customizeNonPsiElementLeftRenderer(ColoredListCellRenderer renderer,
                                                       JList list,
                                                       Object value,
                                                       int index,
                                                       boolean selected,
                                                       boolean hasFocus) {
    if (!(value instanceof NavigationItem)) return false;

    NavigationItem item = (NavigationItem)value;

    TextAttributes attributes = getNavigationItemAttributes(item);

    SimpleTextAttributes nameAttributes = attributes != null ? SimpleTextAttributes.fromTextAttributes(attributes) : null;

    Color color = list.getForeground();
    if (nameAttributes == null) nameAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color);

    renderer.append(item + " ", nameAttributes);
    ItemPresentation itemPresentation = item.getPresentation();
    assert itemPresentation != null;
    renderer.setIcon(itemPresentation.getIcon(true));

    String locationString = itemPresentation.getLocationString();
    if (!StringUtil.isEmpty(locationString)) {
      renderer.append(locationString, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY));
    }
    return true;
  }

  @Override
  protected DefaultListCellRenderer getRightCellRenderer(final Object value) {
    final DefaultListCellRenderer rightRenderer = super.getRightCellRenderer(value);
    if (rightRenderer instanceof PlatformModuleRendererFactory.PlatformModuleRenderer) {
      // that renderer will display file path, but we're showing it ourselves - no need to show twice
      return null;
    }
    return rightRenderer;
  }

  @Override
  protected int getIconFlags() {
    return Iconable.ICON_FLAG_READ_STATUS;
  }
}
