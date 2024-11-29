// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.TreeNodePresentationImpl;
import com.intellij.ui.treeStructure.TreeNodeTextFragment;
import com.intellij.ui.treeStructure.TreeNodeViewModel;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class NodeRenderer extends ColoredTreeCellRenderer {
  protected Icon fixIconIfNeeded(Icon icon, boolean selected, boolean hasFocus) {
    if (icon != null && !StartupUiUtil.isUnderDarcula() && Registry.is("ide.project.view.change.icon.on.selection", true) && selected && hasFocus) {
      return IconLoader.getDarkIcon(icon, true);
    }
    return icon;
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree, @NlsSafe Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (value instanceof TreeNodeViewModel vm) {
      customizeViewModelRenderer((TreeNodePresentationImpl)vm.stateSnapshot().getPresentation(), selected, hasFocus);
    }
    else {
      customizeLegacyRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
    }
  }

  private void customizeViewModelRenderer(@NotNull TreeNodePresentationImpl presentation, boolean selected, boolean hasFocus) {
    setIcon(fixIconIfNeeded(presentation.getIcon(), selected, hasFocus));
    boolean isMain = true;
    for (@NotNull TreeNodeTextFragment fragment : presentation.getFullText()) {
      var simpleTextAttributes = fragment.getAttributes();
      isMain = isMain && !Comparing.equal(simpleTextAttributes.getFgColor(), SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor());
      append(fragment.getText(), simpleTextAttributes, isMain);
    }
    setToolTipText(presentation.getToolTip());
  }

  private void customizeLegacyRenderer(@NotNull JTree tree, @NlsSafe Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    @NlsSafe Object node = TreeUtil.getUserObject(value);

    if (node instanceof NodeDescriptor<?> descriptor) {
      // TODO: use this color somewhere
      Color color = descriptor.getColor();
      setIcon(fixIconIfNeeded(descriptor.getIcon(), selected, hasFocus));
    }

    ItemPresentation p0 = getPresentation(node);

    if (p0 instanceof PresentationData presentation) {
      Color color = node instanceof NodeDescriptor ? ((NodeDescriptor<?>)node).getColor() : null;
      setIcon(fixIconIfNeeded(presentation.getIcon(false), selected, hasFocus));

      final List<PresentableNodeDescriptor.ColoredFragment> coloredText = presentation.getColoredText();
      Color forcedForeground = presentation.getForcedTextForeground();
      if (coloredText.isEmpty()) {
        String text = presentation.getPresentableText();
        if (StringUtil.isEmpty(text)) {
          @NlsSafe String valueSting = value.toString();
          text = valueSting;
        }
        text = tree.convertValueToText(text, selected, expanded, leaf, row, hasFocus);
        SimpleTextAttributes simpleTextAttributes = getSimpleTextAttributes(
          presentation, forcedForeground != null ? forcedForeground : color, node);
        append(text, simpleTextAttributes);
        String location = presentation.getLocationString();
        if (!StringUtil.isEmpty(location)) {
          SimpleTextAttributes attributes = SimpleTextAttributes.merge(simpleTextAttributes, SimpleTextAttributes.GRAYED_ATTRIBUTES);
          append(presentation.getLocationPrefix() + location + presentation.getLocationSuffix(), attributes, false);
        }
      }
      else {
        boolean first = true;
        boolean isMain = true;
        for (PresentableNodeDescriptor.ColoredFragment each : coloredText) {
          SimpleTextAttributes simpleTextAttributes = each.getAttributes();
          if (each.getAttributes().getFgColor() == null && forcedForeground != null) {
            simpleTextAttributes = addColorToSimpleTextAttributes(each.getAttributes(), forcedForeground);
          }
          if (first) {
            final TextAttributesKey textAttributesKey = presentation.getTextAttributesKey();
            if (textAttributesKey != null) {
              TextAttributes forcedAttributes = getScheme().getAttributes(textAttributesKey);
              if (forcedAttributes != null) {
                simpleTextAttributes = SimpleTextAttributes.merge(simpleTextAttributes, SimpleTextAttributes.fromTextAttributes(forcedAttributes));
              }
            }
            first = false;
          }
          // the first grayed text (inactive foreground, regular or small) ends main speed-searchable text
          isMain = isMain && !Comparing.equal(simpleTextAttributes.getFgColor(), SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor());
          append(each.getText(), simpleTextAttributes, isMain);
        }
        String location = presentation.getLocationString();
        if (!StringUtil.isEmpty(location)) {
          append(presentation.getLocationPrefix() + location + presentation.getLocationSuffix(), SimpleTextAttributes.GRAYED_ATTRIBUTES, false);
        }
      }

      setToolTipText(presentation.getTooltip());
    }
    else if (value != null) {
      @NlsSafe String text = value.toString();
      if (node instanceof NodeDescriptor) {
        text = node.toString();
      }
      text = tree.convertValueToText(text, selected, expanded, leaf, row, hasFocus);
      if (text == null) {
        text = "";
      }
      append(text);
      setToolTipText(null);
    }
  }

  protected @Nullable ItemPresentation getPresentation(Object node) {
    return node instanceof PresentableNodeDescriptor ? ((PresentableNodeDescriptor<?>)node).getPresentation() :
           node instanceof NavigationItem ? ((NavigationItem)node).getPresentation() :
           null;
  }

  private static @NotNull EditorColorsScheme getScheme() {
    return EditorColorsManager.getInstance().getSchemeForCurrentUITheme();
  }

  protected @NotNull SimpleTextAttributes getSimpleTextAttributes(@NotNull PresentationData presentation, Color color, @NotNull Object node) {
    SimpleTextAttributes simpleTextAttributes = getSimpleTextAttributes(presentation, getScheme());

    return addColorToSimpleTextAttributes(simpleTextAttributes, color);
  }

  private static SimpleTextAttributes addColorToSimpleTextAttributes(SimpleTextAttributes simpleTextAttributes, Color color) {
    if (color != null) {
      final TextAttributes textAttributes = simpleTextAttributes.toTextAttributes();
      textAttributes.setForegroundColor(color);
      simpleTextAttributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
    }
    return simpleTextAttributes;
  }

  public static SimpleTextAttributes getSimpleTextAttributes(final @Nullable ItemPresentation presentation) {
    return getSimpleTextAttributes(presentation, getScheme());
  }

  private static SimpleTextAttributes getSimpleTextAttributes(final @Nullable ItemPresentation presentation,
                                                              @NotNull EditorColorsScheme colorsScheme)
  {
    if (presentation instanceof ColoredItemPresentation) {
      final TextAttributesKey textAttributesKey = ((ColoredItemPresentation) presentation).getTextAttributesKey();
      if (textAttributesKey == null) return SimpleTextAttributes.REGULAR_ATTRIBUTES;
      final TextAttributes textAttributes = colorsScheme.getAttributes(textAttributesKey);
      return textAttributes == null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.fromTextAttributes(textAttributes);
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

}