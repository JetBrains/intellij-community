/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.util.Comparator;

public abstract class PsiElementListCellRenderer<T extends PsiElement> extends JPanel implements ListCellRenderer, MatcherHolder {

  private static final String LEFT = BorderLayout.WEST;

  private Matcher myMatcher;
  private boolean myFocusBorderEnabled = Registry.is("psi.element.list.cell.renderer.focus.border.enabled");
  protected int myRightComponentWidth;

  protected PsiElementListCellRenderer() {
    super(new BorderLayout());
  }

  private class MyAccessibleContext extends JPanel.AccessibleJPanel {
    @Override
    public String getAccessibleName() {
      LayoutManager lm = PsiElementListCellRenderer.this.getLayout();
      assert lm instanceof BorderLayout;
      Component leftCellRendererComp = ((BorderLayout)lm).getLayoutComponent(LEFT);
      return leftCellRendererComp instanceof Accessible ?
             leftCellRendererComp.getAccessibleContext().getAccessibleName() : super.getAccessibleName();
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new MyAccessibleContext();
    }
    return accessibleContext;
  }

  @Override
  public void setPatternMatcher(final Matcher matcher) {
    myMatcher = matcher;
  }

  protected static Color getBackgroundColor(@Nullable Object value) {
    if (value instanceof PsiElement) {
      final PsiElement psiElement = (PsiElement)value;
      final FileColorManager colorManager = FileColorManager.getInstance(psiElement.getProject());

      if (colorManager.isEnabled()) {
        VirtualFile file = null;
        PsiFile psiFile = psiElement.getContainingFile();

        if (psiFile != null) {
          file = psiFile.getVirtualFile();
        } else if (psiElement instanceof PsiDirectory) {
          file = ((PsiDirectory)psiElement).getVirtualFile();
        }
        final Color fileBgColor = file != null ? colorManager.getRendererBackground(file) : null;

        if (fileBgColor != null) {
          return fileBgColor;
        }
      }
    }

    return UIUtil.getListBackground();
  }

  private class LeftRenderer extends ColoredListCellRenderer {
    private final String myModuleName;
    private final Matcher myMatcher;

    public LeftRenderer(final String moduleName, Matcher matcher) {
      myModuleName = moduleName;
      myMatcher = matcher;
    }

    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      Color bgColor = UIUtil.getListBackground();
      Color color = list.getForeground();
      setPaintFocusBorder(hasFocus && UIUtil.isToUseDottedCellBorder() && myFocusBorderEnabled);
      if (value instanceof PsiElement) {
        T element = (T)value;
        String name = element.isValid() ? getElementText(element) : "INVALID";
        PsiFile psiFile = element.isValid() ? element.getContainingFile() : null;
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

        TextAttributes attributes = getNavigationItemAttributes(value);

        if (isProblemFile) {
          attributes = TextAttributes.merge(new TextAttributes(color, null, JBColor.RED, EffectType.WAVE_UNDERSCORE, Font.PLAIN), attributes);
        }

        SimpleTextAttributes nameAttributes = attributes != null ? SimpleTextAttributes.fromTextAttributes(attributes) : null;

        if (nameAttributes == null) nameAttributes = new SimpleTextAttributes(Font.PLAIN, color);

        assert name != null : "Null name for PSI element " + element + " (by " + PsiElementListCellRenderer.this + ")";
        SpeedSearchUtil.appendColoredFragmentForMatcher(name,  this, nameAttributes, myMatcher, bgColor, selected);
        if (!element.isValid()) {
          append(" Invalid", SimpleTextAttributes.ERROR_ATTRIBUTES);
          return;
        }
        setIcon(PsiElementListCellRenderer.this.getIcon(element));

        String containerText = getContainerTextForLeftComponent(element, name + (myModuleName != null ? myModuleName + "        " : ""));
        if (containerText != null) {
          append(" " + containerText, new SimpleTextAttributes(Font.PLAIN, JBColor.GRAY));
        }
      }
      else if (!customizeNonPsiElementLeftRenderer(this, list, value, index, selected, hasFocus)) {
        setIcon(IconUtil.getEmptyIcon(false));
        append(value == null ? "" : value.toString(), new SimpleTextAttributes(Font.PLAIN, list.getForeground()));
      }
      setBackground(selected ? UIUtil.getListSelectionBackground() : bgColor);
    }
  }

  @Nullable
  protected TextAttributes getNavigationItemAttributes(Object value) {
    TextAttributes attributes = null;

    if (value instanceof NavigationItem) {
      TextAttributesKey attributesKey = null;
      final ItemPresentation presentation = ((NavigationItem)value).getPresentation();
      if (presentation instanceof ColoredItemPresentation) attributesKey = ((ColoredItemPresentation) presentation).getTextAttributesKey();

      if (attributesKey != null) {
        attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(attributesKey);
      }
    }
    return attributes;
  }

  @Override
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    removeAll();
    myRightComponentWidth = 0;
    DefaultListCellRenderer rightRenderer = getRightCellRenderer(value);
    Component rightCellRendererComponent = null;
    JPanel spacer = null;
    if (rightRenderer != null) {
      rightCellRendererComponent = rightRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      add(rightCellRendererComponent, BorderLayout.EAST);
      spacer = new JPanel();
      spacer.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
      add(spacer, BorderLayout.CENTER);
      myRightComponentWidth = rightCellRendererComponent.getPreferredSize().width;
      myRightComponentWidth += spacer.getPreferredSize().width;
    }

    ListCellRenderer leftRenderer = new LeftRenderer(null, myMatcher);
    final Component leftCellRendererComponent = leftRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    add(leftCellRendererComponent, LEFT);
    final Color bg = isSelected ? UIUtil.getListSelectionBackground() : leftCellRendererComponent.getBackground();
    setBackground(bg);
    if (rightCellRendererComponent != null) {
      rightCellRendererComponent.setBackground(bg);
    }
    if (spacer != null) {
      spacer.setBackground(bg);
    }
    return this;
  }

  protected void setFocusBorderEnabled(boolean enabled) {
    myFocusBorderEnabled = enabled;
  }

  protected boolean customizeNonPsiElementLeftRenderer(ColoredListCellRenderer renderer,
                                                       JList list,
                                                       Object value,
                                                       int index,
                                                       boolean selected,
                                                       boolean hasFocus) {
    return false;
  }

  @Nullable
  protected DefaultListCellRenderer getRightCellRenderer(final Object value) {
    if (UISettings.getInstance().SHOW_ICONS_IN_QUICK_NAVIGATION) {
      final DefaultListCellRenderer renderer = ModuleRendererFactory.findInstance(value).getModuleRenderer();
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

  @Nullable
  protected String getContainerTextForLeftComponent(T element, final String name) {
    return getContainerText(element, name);
  }

  @Iconable.IconFlags
  protected abstract int getIconFlags();

  protected Icon getIcon(PsiElement element) {
    return element.getIcon(getIconFlags());
  }

  public Comparator<T> getComparator() {
    return new Comparator<T>() {
      @Override
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
      @Override
      public String fun(Object o) {
        if (o instanceof PsiElement) {
          final String elementText = getElementText((T)o);
          if (includeContainerText) {
            return elementText + " " + getContainerText((T)o, elementText);
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
      @Override
      protected String getElementText(Object o) {
        if (o instanceof PsiElement) {
          final String elementText = PsiElementListCellRenderer.this.getElementText((T)o);
          return elementText + " " + getContainerText((T)o, elementText);
        }
        else {
          return o.toString();
        }
      }
    };
  }
}
