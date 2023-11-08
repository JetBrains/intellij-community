// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.inspectopedia.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.codeInspection.options.*;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.inspectopedia.extractor.data.Inspection;
import com.intellij.inspectopedia.extractor.data.OptionsPanelInfo;
import com.intellij.inspectopedia.extractor.data.Plugin;
import com.intellij.inspectopedia.extractor.data.Plugins;
import com.intellij.inspectopedia.extractor.utils.HtmlUtils;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

final class InspectopediaExtractor implements ApplicationStarter {
  private static final Logger LOG = Logger.getInstance(InspectopediaExtractor.class);
  private static final Map<String, ObjectMapper> ASSETS = new HashMap<>();

  static {
    JsonMapper jsonMapper = JsonMapper.builder()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
      .build();
    ASSETS.put("json", jsonMapper);
  }

  @Override
  public @NonNls String getCommandName() {
    return "inspectopedia-generator";
  }

  @Override
  public void main(@NotNull List<String> args) {
    final int size = args.size();
    if (size != 2) {
      LOG.error("Usage: %s <output directory>".formatted(getCommandName()));
      System.exit(-1);
    }

    ApplicationInfo appInfo = ApplicationInfo.getInstance();
    String IDE_CODE = appInfo.getBuild().getProductCode().toLowerCase(Locale.getDefault());
    String IDE_NAME = appInfo.getVersionName();
    String IDE_VERSION = appInfo.getShortVersion();
    String ASSET_FILENAME = IDE_CODE + "-inspections.";

    final String outputDirectory = args.get(1);
    final Path rootOutputPath = Path.of(outputDirectory).toAbsolutePath();
    final Path outputPath = rootOutputPath.resolve(IDE_CODE);

    try {
      Files.createDirectories(outputPath);
    }
    catch (IOException e) {
      LOG.error("Output directory does not exist and could not be created");
      System.exit(-1);
    }

    if (!Files.exists(outputPath) || !Files.isDirectory(outputPath) || !Files.isWritable(outputPath)) {
      LOG.error("Output path is invalid");
      System.exit(-1);
    }

    try {
      final Project project = ProjectManager.getInstance().getDefaultProject();

      LOG.info("Using project " + project.getName() + ", default: " + project.isDefault());
      final InspectionProjectProfileManager inspectionManager = InspectionProjectProfileManager.getInstance(project);
      final List<ScopeToolState> scopeToolStates = inspectionManager.getCurrentProfile().getAllTools();

      final Map<String, Plugin> availablePlugins = Arrays.stream(PluginManager.getPlugins()).map(
          pluginDescriptor -> new Plugin(pluginDescriptor.getPluginId().getIdString(), pluginDescriptor.getName(),
                                         pluginDescriptor.getVersion())).distinct()
        .collect(Collectors.toMap(Plugin::getId, plugin -> plugin));

      availablePlugins.put(IDE_NAME, new Plugin(IDE_NAME, IDE_NAME, IDE_VERSION));

      for (final ScopeToolState scopeToolState : scopeToolStates) {

        final InspectionToolWrapper<?, ?> wrapper = scopeToolState.getTool();
        final InspectionEP extension = wrapper.getExtension();
        final String pluginId = extension == null ? IDE_NAME : extension.getPluginDescriptor().getPluginId().getIdString();
        final String originalDescription = wrapper.loadDescription();
        final String[] description = originalDescription == null ? new String[]{""} : originalDescription.split("<!-- tooltip end -->");

        List<OptionsPanelInfo> panelInfo = null;
        try {
          InspectionProfileEntry tool = wrapper.getTool();
          final OptPane panel = tool.getOptionsPane();

          if (!panel.equals(OptPane.EMPTY)) {
            LOG.info("Saving options panel for " + wrapper.getShortName());
            panelInfo = retrievePanelStructure(panel, tool.getOptionController());
          }
        }
        catch (Throwable t) {
          LOG.info("Cannot create options panel " + wrapper.getShortName(), t);
        }
        final String language = wrapper.getLanguage();
        final String briefDescription = HtmlUtils.cleanupHtml(description[0], language);
        final String extendedDescription = description.length > 1 ? HtmlUtils.cleanupHtml(description[1], language) : null;
        final Inspection inspection = new Inspection(wrapper.getShortName(), wrapper.getDisplayName(), wrapper.getDefaultLevel().getName(),
                                                     language, briefDescription,
                                                     extendedDescription, Arrays.asList(wrapper.getGroupPath()), wrapper.applyToDialects(),
                                                     wrapper.isCleanupTool(), wrapper.isEnabledByDefault(), panelInfo);

        availablePlugins.get(pluginId).addInspection(inspection);
      }

      var sortedPlugins = availablePlugins.values().stream()
        .sorted(Comparator.comparing(Plugin::getId))
        .peek(plugin -> {
          plugin.inspections.sort(null);
        }).toList();
      final Plugins pluginsData = new Plugins(sortedPlugins, IDE_CODE, IDE_NAME, IDE_VERSION);

      for (final String ext : ASSETS.keySet()) {
        String data = "";
        try {
          data = ASSETS.get(ext).writeValueAsString(pluginsData);
        }
        catch (JsonProcessingException e) {
          LOG.error("Cannot serialize " + ext.toUpperCase(Locale.getDefault()), e);
          System.exit(-1);
        }

        final Path outPath = outputPath.resolve(ASSET_FILENAME + ext);

        try {
          Files.writeString(outPath, data);
        }
        catch (IOException e) {
          LOG.error("Cannot write " + outPath, e);
          System.exit(-1);
        }
        LOG.info("Inspections info saved in " + outPath);
      }
    }
    catch (Exception e) {
      LOG.error(e.getMessage(), e);
      System.exit(-1);
    }
    System.exit(0);
  }

  private static @Nullable LocMessage getMyText(final @NotNull OptComponent cmp) {
    if (cmp instanceof OptCheckbox checkbox) {
      return checkbox.label();
    }
    else if (cmp instanceof OptString string) {
      return string.splitLabel();
    }
    else if (cmp instanceof OptNumber number) {
      return number.splitLabel();
    }
    else if (cmp instanceof OptExpandableString expandableString) {
      return expandableString.label();
    }
    else if (cmp instanceof OptStringList list) {
      return list.label();
    }
    else if (cmp instanceof OptTable table) {
      return table.label();
    }
    else if (cmp instanceof OptTableColumn column) {
      return column.name();
    }
    else if (cmp instanceof OptTab tab) {
      return tab.label();
    }
    else if (cmp instanceof OptDropdown dropdown) {
      return dropdown.splitLabel();
    }
    else if (cmp instanceof OptGroup group) {
      return group.label();
    }
    else if (cmp instanceof OptSettingLink link) {
      return link.displayName();
    }
    else {
      return null;
    }
  }

  private static @NotNull List<@NotNull OptionsPanelInfo> retrievePanelStructure(@NotNull OptPane pane,
                                                                                 @NotNull OptionController controller) {
    List<OptionsPanelInfo> children = new ArrayList<>();
    for (OptRegularComponent component : pane.components()) {
      children.add(retrievePanelStructure(component, controller));
    }
    return children;
  }

  private static @NotNull OptionsPanelInfo retrievePanelStructure(@NotNull OptComponent component, @NotNull OptionController controller) {
    final OptionsPanelInfo result = new OptionsPanelInfo();
    result.type = component.getClass().getSimpleName();
    result.value = component instanceof OptControl control ? controller.getOption(control.bindId()) : null;
    if (component instanceof OptDropdown dropdown) {
      if (result.value != null) {
        OptDropdown.Option option = dropdown.findOption(result.value);
        result.value = option == null ? null : option.label().label();
      }
      result.content = ContainerUtil.map(dropdown.options(), opt -> opt.label().label());
    }
    LocMessage text = getMyText(component);
    result.text = text == null ? null : text.label();
    if (component instanceof OptDescribedComponent describedComponent) {
      HtmlChunk description = describedComponent.description();
      result.description = description == null ? null : description.toString();
    }
    List<OptionsPanelInfo> children = new ArrayList<>();
    for (OptComponent child : component.children()) {
      children.add(retrievePanelStructure(child, controller));
    }
    if (!children.isEmpty()) {
      result.children = children;
    }
    return result;
  }
}