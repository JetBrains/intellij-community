// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.settings;

import com.intellij.application.options.colors.*;
import com.intellij.diff.util.DiffLineSeparatorRenderer;
import com.intellij.diff.util.TextDiffTypeFactory;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorAndFontDescriptorsProvider;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class DiffColorsPageFactory implements ColorAndFontPanelFactory, ColorAndFontDescriptorsProvider, DisplayPrioritySortable {
  @Override
  public @NotNull NewColorAndFontPanel createPanel(@NotNull ColorAndFontOptions options) {
    final SchemesPanel schemesPanel = new SchemesPanel(options);

    CompositeColorDescriptionPanel descriptionPanel = new CompositeColorDescriptionPanel();
    descriptionPanel.addDescriptionPanel(new ColorAndFontDescriptionPanel(), it -> it instanceof ColorAndFontDescription);
    descriptionPanel.addDescriptionPanel(new DiffColorDescriptionPanel(options), it -> it instanceof TextAttributesDescription);

    final OptionsPanelImpl optionsPanel = new OptionsPanelImpl(options, schemesPanel, getDiffGroup(), descriptionPanel);
    final DiffPreviewPanel previewPanel = new DiffPreviewPanel();

    schemesPanel.addListener(new ColorAndFontSettingsListener.Abstract() {
      @Override
      public void schemeChanged(final @NotNull Object source) {
        previewPanel.setColorScheme(options.getSelectedScheme());
        optionsPanel.updateOptionsList();
      }
    });

    return new NewColorAndFontPanel(schemesPanel, optionsPanel, previewPanel, getDiffGroup(), null, null);
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    List<TextDiffTypeFactory.TextDiffTypeImpl> diffTypes = TextDiffTypeFactory.getAllDiffTypes();
    return ContainerUtil.map2Array(diffTypes, AttributesDescriptor.class, type ->
      new AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.vcs.diff.type.tag.prefix") + type.getName(),
                               type.getKey()));
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    List<ColorDescriptor> descriptors = new ArrayList<>();

    descriptors.add(new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.vcs.diff.separator.wave.foreground"),
                                        DiffLineSeparatorRenderer.FOREGROUND, ColorDescriptor.Kind.FOREGROUND));

    return descriptors.toArray(ColorDescriptor.EMPTY_ARRAY);
  }

  @Override
  public @NotNull String getPanelDisplayName() {
    return getDiffGroup();
  }

  @Override
  public @NotNull String getDisplayName() {
    return getDiffGroup();
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.COMMON_SETTINGS;
  }

  public static @Nls String getDiffGroup() {
    return ApplicationBundle.message("title.diff");
  }
}
