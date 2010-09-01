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

package com.intellij.ide.util;

import com.intellij.ide.ui.UISettings;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;

public abstract class PsiElementListCellRenderer<T extends PsiElement> extends JPanel implements ListCellRenderer {
  protected PsiElementListCellRenderer() {
    super(new BorderLayout());
  }

  private class LeftRenderer extends ColoredListCellRenderer {
    private final String myModuleName;

    public LeftRenderer(final String moduleName) {
      myModuleName = moduleName;
    }

    protected void customizeCellRenderer(
      JList list,
      Object value,
      int index,
      boolean selected,
      boolean hasFocus
      ) {
      Color bgColor = UIUtil.getListBackground();
      if (value instanceof PsiElement) {
        T element = (T)value;
        String name = getElementText((T)element);
        Color color = list.getForeground();
        PsiFile psiFile = element.getContainingFile();
        boolean isProblemFile = false;

        if (psiFile != null) {
          VirtualFile vFile = psiFile.getVirtualFile();
          if (vFile != null) {
            if (WolfTheProblemSolver.getInstance(psiFile.getProject()).isProblemFile(vFile)) {
              isProblemFile = true;
            }
            FileStatus status = FileStatusManager.getInstance(psiFile.getProject()).getStatus(vFile);
            color = status.getColor();

            final FileColorManager colorManager = FileColorManager.getInstance(psiFile.getProject());
            if (colorManager.isEnabled()) {
              final Color fileBgColor = colorManager.getRendererBackground(psiFile);
              bgColor = fileBgColor == null ? bgColor : fileBgColor;
            }
          }
        }

        TextAttributes attributes = null;

        if (value instanceof NavigationItem) {
          TextAttributesKey attributesKey = null;
          final ItemPresentation presentation = ((NavigationItem)value).getPresentation();
          if (presentation != null) attributesKey = presentation.getTextAttributesKey();

          if (attributesKey != null) {
            attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(attributesKey);
          }
        }

        if (isProblemFile) {
          attributes = TextAttributes.merge(new TextAttributes(color, null, Color.red, EffectType.WAVE_UNDERSCORE, Font.PLAIN),attributes);
        }

        SimpleTextAttributes nameAttributes = attributes != null ? SimpleTextAttributes.fromTextAttributes(attributes) : null;

        if (nameAttributes == null)  nameAttributes = new SimpleTextAttributes(Font.PLAIN, color);

        assert name != null: "Null name for PSI element " + element;
        append(name, nameAttributes);
        setIcon(PsiElementListCellRenderer.this.getIcon(element));

        String containerText = getContainerText(element, name + (myModuleName != null ? myModuleName + "        " : ""));
        if (containerText != null) {
          append(" " + containerText, new SimpleTextAttributes(Font.PLAIN, Color.GRAY));
        }
      }
      else {
        setIcon(IconUtil.getEmptyIcon(false));
        append(value == null ? "" : value.toString(), new SimpleTextAttributes(Font.PLAIN, list.getForeground()));
      }
      setPaintFocusBorder(false);
      setBackground(selected ? UIUtil.getListSelectionBackground() : bgColor);
    }
  }

  public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    removeAll();
    String moduleName = null;
    DefaultListCellRenderer rightRenderer = getRightCellRenderer();
    final Component leftCellRendererComponent =
      new LeftRenderer(moduleName).getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    if (rightRenderer != null) {
      final Component rightCellRendererComponent =
        rightRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      rightCellRendererComponent.setBackground(isSelected ? UIUtil.getListSelectionBackground() : leftCellRendererComponent.getBackground());
      add(rightCellRendererComponent, BorderLayout.EAST);
      moduleName = rightRenderer.getText();
      final JPanel spacer = new JPanel();
      spacer.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
      spacer.setBackground(isSelected ? UIUtil.getListSelectionBackground() : leftCellRendererComponent.getBackground());
      add(spacer, BorderLayout.CENTER);
    }
    add(leftCellRendererComponent, BorderLayout.WEST);
    setBackground(isSelected ? UIUtil.getListSelectionBackground() : leftCellRendererComponent.getBackground());
    return this;
  }

  @Nullable
  protected DefaultListCellRenderer getRightCellRenderer() {
    if (UISettings.getInstance().SHOW_ICONS_IN_QUICK_NAVIGATION) {
      final DefaultListCellRenderer renderer = ModuleRendererFactory.getInstance().getModuleRenderer();
      if (renderer instanceof PlatformModuleRendererFactory.PlatformModuleRenderer) {
        // it won't display any new information
        return null;
      }
      return renderer;
    }
    return null;
  }

  public abstract String getElementText(T element);

  @Nullable
  protected abstract String getContainerText(T element, final String name);

  protected abstract int getIconFlags();

  protected Icon getIcon(PsiElement element) {
    return element.getIcon(getIconFlags());
  }

  public Comparator<T> getComparator() {
    return new Comparator<T>() {
      public int compare(T o1, T o2) {
        return getComparingObject(o1).compareTo(getComparingObject(o2));
      }
    };
  }

  @NotNull
  public Comparable getComparingObject(T element) {
    String elementText = getElementText(element);
    String containerText = getContainerText(element, elementText);
    return containerText != null ? elementText + " " + containerText : elementText;
  }

  public void installSpeedSearch(PopupChooserBuilder builder) {
    installSpeedSearch(builder, false);
  }

  public void installSpeedSearch(PopupChooserBuilder builder, final boolean includeContainerText) {
    builder.setFilteringEnabled(new Function<Object, String>() {
      public String fun(Object o) {
        if (o instanceof PsiElement) {
          final String elementText = PsiElementListCellRenderer.this.getElementText((T)o);
          if (includeContainerText) {
            return elementText + " " + getContainerText((T) o, elementText);
          }
          return elementText;
        }
        else {
          return o.toString();
        }
      }
    });
  }

  /**
   * User {@link #installSpeedSearch(com.intellij.openapi.ui.popup.PopupChooserBuilder)} instead
   */
  @Deprecated
  public void installSpeedSearch(JList list) {
    new ListSpeedSearch(list) {
      protected String getElementText(Object o) {
        if (o instanceof PsiElement) {
          final String elementText = PsiElementListCellRenderer.this.getElementText((T)o);
          return elementText + " " + getContainerText((T) o, elementText);
        }
        else {
          return o.toString();
        }
      }
    };
  }
}
