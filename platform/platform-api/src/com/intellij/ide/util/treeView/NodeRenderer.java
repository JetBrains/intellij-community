// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.TreeNodePresentationImpl;
import com.intellij.ui.treeStructure.TreeNodeViewModel;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class NodeRenderer extends ColoredTreeCellRenderer {
  protected Icon fixIconIfNeeded(Icon icon, boolean selected, boolean hasFocus) {
    var dark = icon != null && !StartupUiUtil.INSTANCE.isDarkTheme() && Registry.is("ide.project.view.change.icon.on.selection", true) && selected && hasFocus;
    return dark ? IconLoader.getDarkIcon(icon, true) : icon;
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

  private void customizeViewModelRenderer(TreeNodePresentationImpl presentation, boolean selected, boolean hasFocus) {
    setIcon(fixIconIfNeeded(presentation.getIcon(), selected, hasFocus));

    var isMain = true;
    for (var fragment : presentation.getFullText()) {
      var simpleTextAttributes = fragment.getAttributes();
      isMain = isMain && !Comparing.equal(simpleTextAttributes.getFgColor(), SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor());
      @NlsSafe var text = fragment.getText();
      append(text, simpleTextAttributes, isMain);
    }

    @NlsSafe var toolTip = presentation.getToolTip();
    setToolTipText(toolTip);
  }

  private void customizeLegacyRenderer(JTree tree, Object v, boolean selected, boolean expanded, boolean leaf, int row, boolean focused) {
    var node = TreeUtil.getUserObject(v);

    if (node instanceof NodeDescriptor<?> descriptor) {
      setIcon(fixIconIfNeeded(descriptor.getIcon(), selected, focused));
    }

    if (getPresentation(node) instanceof PresentationData presentation) {
      var color = node instanceof NodeDescriptor ? ((NodeDescriptor<?>)node).getColor() : null;
      setIcon(fixIconIfNeeded(presentation.getIcon(false), selected, focused));

      var coloredText = presentation.getColoredText();
      var forcedForeground = presentation.getForcedTextForeground();
      if (coloredText.isEmpty()) {
        var text = presentation.getPresentableText();
        if (StringUtil.isEmpty(text)) {
          @NlsSafe var valueSting = v.toString();
          text = valueSting;
        }
        text = tree.convertValueToText(text, selected, expanded, leaf, row, focused);
        var simpleTextAttributes = getSimpleTextAttributes(presentation, forcedForeground != null ? forcedForeground : color, node);
        append(text, simpleTextAttributes);
        var location = presentation.getLocationString();
        if (!StringUtil.isEmpty(location)) {
          var attributes = SimpleTextAttributes.merge(simpleTextAttributes, SimpleTextAttributes.GRAYED_ATTRIBUTES);
          append(presentation.getLocationPrefix() + location + presentation.getLocationSuffix(), attributes, false);
        }
      }
      else {
        var first = true;
        var isMain = true;
        for (var each : coloredText) {
          var simpleTextAttributes = each.getAttributes();
          if (each.getAttributes().getFgColor() == null && forcedForeground != null) {
            simpleTextAttributes = addColorToSimpleTextAttributes(each.getAttributes(), forcedForeground);
          }
          if (first) {
            var textAttributesKey = presentation.getTextAttributesKey();
            if (textAttributesKey != null) {
              var forcedAttributes = getScheme().getAttributes(textAttributesKey);
              if (forcedAttributes != null) {
                simpleTextAttributes = SimpleTextAttributes.merge(simpleTextAttributes, SimpleTextAttributes.fromTextAttributes(forcedAttributes));
              }
            }
            first = false;
          }
          // the first grayed text (inactive foreground, regular or small) ends the main speed-searchable text
          isMain = isMain && !Comparing.equal(simpleTextAttributes.getFgColor(), SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor());
          append(each.getText(), simpleTextAttributes, isMain);
        }
        var location = presentation.getLocationString();
        if (!StringUtil.isEmpty(location)) {
          append(presentation.getLocationPrefix() + location + presentation.getLocationSuffix(), SimpleTextAttributes.GRAYED_ATTRIBUTES, false);
        }
      }

      setToolTipText(presentation.getTooltip());
    }
    else if (v != null) {
      @NlsSafe var text = v.toString();
      if (node instanceof NodeDescriptor) {
        text = node.toString();
      }
      text = tree.convertValueToText(text, selected, expanded, leaf, row, focused);
      if (text == null) {
        text = "";
      }
      append(text);
      setToolTipText(null);
    }
  }

  @Contract("null->null")
  protected @Nullable ItemPresentation getPresentation(Object node) {
    return node instanceof PresentableNodeDescriptor<?> pnd ? pnd.getPresentation() :
           node instanceof NavigationItem ni ? ni.getPresentation() :
           null;
  }

  private static EditorColorsScheme getScheme() {
    return EditorColorsManager.getInstance().getSchemeForCurrentUITheme();
  }

  protected @NotNull SimpleTextAttributes getSimpleTextAttributes(@NotNull PresentationData presentation, Color color, @NotNull Object node) {
    var simpleTextAttributes = getSimpleTextAttributes(presentation, getScheme());
    return addColorToSimpleTextAttributes(simpleTextAttributes, color);
  }

  private static SimpleTextAttributes addColorToSimpleTextAttributes(SimpleTextAttributes simpleTextAttributes, Color color) {
    if (color != null) {
      var textAttributes = simpleTextAttributes.toTextAttributes();
      if (simpleTextAttributes.useFaded()) {
         color = ColorUtil.faded(color);
      }
      textAttributes.setForegroundColor(color);
      simpleTextAttributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
    }
    return simpleTextAttributes;
  }

  public static SimpleTextAttributes getSimpleTextAttributes(@Nullable ItemPresentation presentation) {
    return getSimpleTextAttributes(presentation, getScheme());
  }

  private static SimpleTextAttributes getSimpleTextAttributes(@Nullable ItemPresentation presentation, EditorColorsScheme colorsScheme) {
    if (presentation instanceof ColoredItemPresentation cip) {
      var textAttributesKey = cip.getTextAttributesKey();
      if (textAttributesKey != null) {
        var textAttributes = colorsScheme.getAttributes(textAttributesKey);
        if (textAttributes != null) {
          return SimpleTextAttributes.fromTextAttributes(textAttributes);
        }
      }
    }

    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }
}
