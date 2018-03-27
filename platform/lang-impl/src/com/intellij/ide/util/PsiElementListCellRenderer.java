/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer;
import com.intellij.openapi.project.Project;
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
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
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
import java.util.regex.Pattern;

public abstract class PsiElementListCellRenderer<T extends PsiElement> extends JPanel implements ListCellRenderer {
  private static final String LEFT = BorderLayout.WEST;
  private static final Pattern CONTAINER_PATTERN = Pattern.compile("(\\(in |\\()?([^)]*)(\\))?");

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

  protected static Color getBackgroundColor(@Nullable Object value) {
    if (value instanceof PsiElement) {
      PsiElement psiElement = (PsiElement)value;
      Project project = psiElement.getProject();

      VirtualFile file = null;
      PsiFile psiFile = psiElement.getContainingFile();

      if (psiFile != null) {
        file = psiFile.getVirtualFile();
      }
      else if (psiElement instanceof PsiDirectory) {
        file = ((PsiDirectory)psiElement).getVirtualFile();
      }
      Color fileBgColor = file != null ? EditorTabbedContainer.calcTabColor(project, file) : null;
      if (fileBgColor != null) {
        return fileBgColor;
      }
    }

    return UIUtil.getListBackground();
  }

  public static class ItemMatchers {
    @Nullable public final Matcher nameMatcher;
    @Nullable public final Matcher locationMatcher;

    public ItemMatchers(@Nullable Matcher nameMatcher, @Nullable Matcher locationMatcher) {
      this.nameMatcher = nameMatcher;
      this.locationMatcher = locationMatcher;
    }
  }

  private class LeftRenderer extends ColoredListCellRenderer {
    private final String myModuleName;
    private final ItemMatchers myMatchers;

    public LeftRenderer(final String moduleName, @NotNull ItemMatchers matchers) {
      myModuleName = moduleName;
      myMatchers = matchers;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
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
            Project project = psiFile.getProject();
            if (WolfTheProblemSolver.getInstance(project).isProblemFile(vFile)) {
              isProblemFile = true;
            }
            FileStatus status = FileStatusManager.getInstance(project).getStatus(vFile);
            color = status.getColor();

            Color fileBgColor = EditorTabbedContainer.calcTabColor(project, vFile);
            bgColor = fileBgColor == null ? bgColor : fileBgColor;
          }
        }

        TextAttributes attributes = element.isValid() ? getNavigationItemAttributes(value) : null;

        SimpleTextAttributes nameAttributes = attributes != null ? SimpleTextAttributes.fromTextAttributes(attributes) : null;

        if (nameAttributes == null) nameAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color);

        assert name != null : "Null name for PSI element " + element + " (by " + PsiElementListCellRenderer.this + ")";
        SpeedSearchUtil.appendColoredFragmentForMatcher(name, this, nameAttributes, myMatchers.nameMatcher, bgColor, selected);
        if (!element.isValid()) {
          append(" Invalid", SimpleTextAttributes.ERROR_ATTRIBUTES);
          return;
        }
        setIcon(PsiElementListCellRenderer.this.getIcon(element));

        String containerText = getContainerTextForLeftComponent(element, name + (myModuleName != null ? myModuleName + "        " : ""));
        if (containerText != null) {
          appendLocationText(selected, bgColor, isProblemFile, containerText);
        }
      }
      else if (!customizeNonPsiElementLeftRenderer(this, list, value, index, selected, hasFocus)) {
        setIcon(IconUtil.getEmptyIcon(false));
        append(value == null ? "" : value.toString(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.getForeground()));
      }
      setBackground(selected ? UIUtil.getListSelectionBackground() : bgColor);
    }

    private void appendLocationText(boolean selected, Color bgColor, boolean isProblemFile, String containerText) {
      SimpleTextAttributes locationAttrs = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY);
      if (isProblemFile) {
        SimpleTextAttributes wavedAttributes = SimpleTextAttributes.merge(new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, JBColor.GRAY, JBColor.RED), locationAttrs);
        java.util.regex.Matcher matcher = CONTAINER_PATTERN.matcher(containerText);
        if (matcher.matches()) {
          String prefix = matcher.group(1);
          SpeedSearchUtil.appendColoredFragmentForMatcher(" " + ObjectUtils.notNull(prefix, ""), this, locationAttrs, myMatchers.locationMatcher, bgColor, selected);

          String strippedContainerText = matcher.group(2);
          SpeedSearchUtil.appendColoredFragmentForMatcher(ObjectUtils.notNull(strippedContainerText, ""), this, wavedAttributes, myMatchers.locationMatcher, bgColor, selected);

          String suffix = matcher.group(3);
          if (suffix != null) {
            SpeedSearchUtil.appendColoredFragmentForMatcher(suffix, this, locationAttrs, myMatchers.locationMatcher, bgColor, selected);
          }
          return;
        }
        locationAttrs = wavedAttributes;
      }
      SpeedSearchUtil.appendColoredFragmentForMatcher(" " + containerText, this, locationAttrs, myMatchers.locationMatcher, bgColor, selected);
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

    ListCellRenderer leftRenderer = new LeftRenderer(null, value == null ? new ItemMatchers(null, null) : getItemMatchers(list, value));
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

  @NotNull
  protected ItemMatchers getItemMatchers(@NotNull JList list, @NotNull Object value) {
    return new ItemMatchers(MatcherHolder.getAssociatedMatcher(list), null);
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
    if (UISettings.getInstance().getShowIconInQuickNavigation()) {
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
    //noinspection unchecked
    return Comparator.comparing(this::getComparingObject);
  }

  @NotNull
  public Comparable getComparingObject(T element) {
    return ReadAction.compute(() -> {
      String elementText = getElementText(element);
      String containerText = getContainerText(element, elementText);
      return containerText != null ? elementText + " " + containerText : elementText;
    });
  }

  public void installSpeedSearch(PopupChooserBuilder builder) {
    installSpeedSearch(builder, false);
  }

  public void installSpeedSearch(PopupChooserBuilder builder, final boolean includeContainerText) {
    builder.setFilteringEnabled(o -> {
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
