package com.intellij.inspectopedia.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.inspectopedia.extractor.data.Inspection;
import com.intellij.inspectopedia.extractor.data.OptionsPanelInfo;
import com.intellij.inspectopedia.extractor.data.Plugin;
import com.intellij.inspectopedia.extractor.data.Plugins;
import com.intellij.inspectopedia.extractor.utils.HtmlUtils;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class InspectopediaExtractor implements ApplicationStarter {
  private static final Logger LOG = Logger.getInstance(InspectopediaExtractor.class);
  public static final String IDE_CODE = ApplicationInfo.getInstance().getBuild().getProductCode().toLowerCase(Locale.getDefault());
  public static final String IDE_NAME = ApplicationInfo.getInstance().getVersionName();
  public static final String IDE_VERSION = ApplicationInfo.getInstance().getShortVersion();
  private static final String ASSET_FILENAME = IDE_CODE + "-inspections.";
  private static final Map<String, ObjectMapper> ASSETS = new HashMap<>();

  static {
/*    final XmlMapper xmlMapper = new XmlMapper();
    xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
    xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
    ASSETS.put("xml", xmlMapper);*/
    final JsonMapper jsonMapper = new JsonMapper();
    jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
    ASSETS.put("json", jsonMapper);
  }

  @Nullable
  public static Language findLanguage(final @Nullable String id) {
    if (id == null) return null;
    Language lang = Language.findLanguageByID(id);
    if (lang != null) return lang;

    lang = Language.findLanguageByID(id.toLowerCase());
    if (lang != null) return lang;

    lang = Language.findLanguageByID(id.toUpperCase());
    if (lang != null) return lang;

    return Language.findLanguageByID(StringUtil.capitalize(id));
  }

  @Override
  public @NonNls String getCommandName() {
    return "inspectopedia-generator";
  }

  @Override
  public void main(@NotNull List<String> args) {
    final int size = args.size();
    if (size != 2) {
      LOG.error("Usage: %s <output directory>", getCommandName());
      System.exit(-1);
    }

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

    final Project project = ProjectManager.getInstance().getDefaultProject();

    LOG.info("Using project " + project.getName() + ", default: " + project.isDefault());
    final InspectionProjectProfileManager inspectionManager = InspectionProjectProfileManager.getInstance(project);
    final List<ScopeToolState> scopeToolStates = inspectionManager.getCurrentProfile().getAllTools();

    final Map<String, Plugin> availablePlugins = Arrays.stream(PluginManager.getPlugins()).map(
      pluginDescriptor -> new Plugin(pluginDescriptor.getPluginId().getIdString(), pluginDescriptor.getName(),
                                     pluginDescriptor.getVersion())).distinct().collect(Collectors.toMap(Plugin::getId, plugin -> plugin));

    availablePlugins.put(IDE_NAME, new Plugin(IDE_NAME, IDE_NAME, IDE_VERSION));

    for (final ScopeToolState scopeToolState : scopeToolStates) {

      final InspectionToolWrapper<?, ?> wrapper = scopeToolState.getTool();
      final InspectionEP extension = wrapper.getExtension();
      final String pluginId = extension == null ? IDE_NAME : extension.getPluginDescriptor().getPluginId().getIdString();
      final String originalDescription = wrapper.loadDescription();
      final String[] description = originalDescription == null ? new String[]{""} : originalDescription.split("<!-- tooltip end -->");

      OptionsPanelInfo panelInfo = null;
      try {
        final JComponent panel = wrapper.getTool().createOptionsPanel();

        if (panel != null) {
          LOG.info("Saving options panel for " + wrapper.getShortName());
          panelInfo = retrievePanelStructure(panel);
        }
      }
      catch (Throwable t) {
        LOG.info("Cannot create options panel " + wrapper.getShortName(), t);
      }
      final Language language = findLanguage(wrapper.getLanguage());
      final String briefDescription = HtmlUtils.cleanupHtml(description[0], language);
      final String extendedDescription = description.length > 1 ? HtmlUtils.cleanupHtml(description[1], language) : null;

      final Inspection inspection = new Inspection(wrapper.getShortName(), wrapper.getDisplayName(), wrapper.getDefaultLevel().getName(),
                                                   language == null ? null : language.getDisplayName(), briefDescription,
                                                   extendedDescription, Arrays.asList(wrapper.getGroupPath()), wrapper.applyToDialects(),
                                                   wrapper.isCleanupTool(), wrapper.isEnabledByDefault(), panelInfo);

      availablePlugins.get(pluginId).addInspection(inspection);
    }

    final Plugins pluginsData = new Plugins(List.copyOf(availablePlugins.values()), IDE_CODE, IDE_NAME, IDE_VERSION);

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

    System.exit(0);
  }

  @Nullable
  private static String getMyText(final @NotNull Object object) {
    if (object instanceof AbstractButton) {
      return ((AbstractButton)object).getText();
    }
    else if (object instanceof JEditorPane) {
      return ((JEditorPane)object).getText();
    }
    else if (object instanceof JLabel) {
      return ((JLabel)object).getText();
    }
    else if (object instanceof JTextComponent) {
      return ((JTextComponent)object).getText();
    }
    else if (object instanceof Frame) {
      return ((Frame)object).getTitle();
    }
    else if (object instanceof Dialog) {
      return ((Dialog)object).getTitle();
    }
    else if (object instanceof JInternalFrame) {
      return ((JInternalFrame)object).getTitle();
    }
    else if (object instanceof TitledBorder) {
      return ((TitledBorder)object).getTitle();
    }
    else if (object instanceof HyperlinkLabel) {
      return ((HyperlinkLabel)object).getText();
    }
    else {
      return null;
    }
  }

  @SuppressWarnings("rawtypes")
  @NotNull
  private static OptionsPanelInfo retrievePanelStructure(final @NotNull Component ofWhat) {
    final OptionsPanelInfo result = new OptionsPanelInfo();
    result.type = ofWhat.getClass().getSimpleName();
    result.text = getMyText(ofWhat);

    if (ofWhat instanceof JList || ofWhat instanceof JComboBox) {
      final ListModel model = ofWhat instanceof JList ? ((JList)ofWhat).getModel() : ((JComboBox)ofWhat).getModel();
      result.children = new ArrayList<>();
      for (int i = 0; i < model.getSize(); i++) {
        result.children.add(new OptionsPanelInfo("ListItem", String.valueOf(model.getElementAt(i))));
      }
    }
    else if (ofWhat instanceof JTable) {
      final JTable table = (JTable)ofWhat;
      result.children = new ArrayList<>();
      for (int i = 0; i < table.getModel().getRowCount(); i++) {
        final StringBuilder sb = new StringBuilder();
        for (int j = 0; j < table.getModel().getColumnCount(); j++) {
          sb.append(table.getModel().getValueAt(i, j));
        }
        result.children.add(new OptionsPanelInfo("TableItem", sb.toString()));
      }
    }
    else if (ofWhat instanceof Container) {
      final Component[] children = ((Container)ofWhat).getComponents();
      if (children != null) {
        result.children = new ArrayList<>();
        for (final Component c : children) {
          result.children.add(retrievePanelStructure(c));
        }
      }
    }
    return result;
  }
}