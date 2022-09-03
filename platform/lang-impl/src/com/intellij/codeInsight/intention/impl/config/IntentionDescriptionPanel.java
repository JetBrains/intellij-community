// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane;
import com.intellij.profile.codeInspection.ui.DescriptionEditorPaneKt;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI.PanelFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

// used in Rider
public class IntentionDescriptionPanel {
  private static final Logger LOG = Logger.getInstance(IntentionDescriptionPanel.class);
  private JPanel myPanel;

  private JPanel myAfterPanel;
  private JPanel myBeforePanel;
  private DescriptionEditorPane myDescriptionBrowser;
  private JPanel myPoweredByPanel;
  private JPanel myPoweredByWrapper;
  private final List<IntentionUsagePanel> myBeforeUsagePanels = new ArrayList<>();
  private final List<IntentionUsagePanel> myAfterUsagePanels = new ArrayList<>();
  @NonNls private static final String BEFORE_TEMPLATE = "before.java.template";
  @NonNls private static final String AFTER_TEMPLATE = "after.java.template";

  public IntentionDescriptionPanel() {
    myPanel = new JPanel(new GridBagLayout());
    final var constraint = new GridBag()
      .setDefaultInsets(UIUtil.LARGE_VGAP, 0, 0, 0)
      .setDefaultFill(GridBagConstraints.BOTH)
      .setDefaultWeightY(0.5)
      .setDefaultWeightX(1.0);

    myDescriptionBrowser = new DescriptionEditorPane();
    final var descriptionScrollPane = ScrollPaneFactory.createScrollPane(myDescriptionBrowser);
    descriptionScrollPane.setBorder(null);
    myPanel.add(descriptionScrollPane, constraint.nextLine().weighty(1.0).insetTop(0));

    myBeforePanel = new JPanel();
    myPanel.add(PanelFactory.panel(myBeforePanel)
                  .withLabel(CodeInsightBundle.message("border.title.before"))
                  .moveLabelOnTop()
                  .resizeX(true)
                  .resizeY(true)
                  .createPanel(),
                constraint.nextLine()
    );

    myAfterPanel = new JPanel();
    myPanel.add(PanelFactory.panel(myAfterPanel)
                  .withLabel(CodeInsightBundle.message("border.title.after"))
                  .moveLabelOnTop()
                  .resizeX(true)
                  .resizeY(true)
                  .createPanel(),
                constraint.nextLine()
    );

    myPoweredByPanel = new JPanel(new BorderLayout());
    myPoweredByWrapper = PanelFactory.panel(myPoweredByPanel)
      .withLabel(CodeInsightBundle.message("powered.by"))
      .resizeX(true)
      .createPanel();
    myPanel.add(myPoweredByWrapper, constraint.nextLine().weighty(0.0));

    myDescriptionBrowser.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        try {
          URI url = new URI(e.getDescription());
          if (url.getScheme().equals("settings")) {
            DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(context -> {
              if (context != null) {
                Settings settings = Settings.KEY.getData(context);
                SearchTextField searchTextField = SearchTextField.KEY.getData(context);
                String configId = url.getHost();
                String search = url.getQuery();
                if (settings != null) {
                  Configurable configurable = settings.find(configId);
                  settings.select(configurable).doWhenDone(() -> {
                    if (searchTextField != null && search != null) searchTextField.setText(search);
                  });
                } else {
                  final Project project = context.getData(CommonDataKeys.PROJECT);
                  ShowSettingsUtilImpl.showSettingsDialog(project, configId, search);
                }
              }
            });
          }
          else {
            BrowserUtil.browse(url);
          }
        }
        catch (URISyntaxException ex) {
          LOG.error(ex);
        }
      }
    });
  }

  public void reset(IntentionActionMetaData actionMetaData, String filter)  {
    try {
      final TextDescriptor url = actionMetaData.getDescription();
      final String description = StringUtil.isEmpty(url.getText()) ?
                                 CodeInsightBundle.message("under.construction.string") :
                                 SearchUtil.markup(url.getText(), filter);
      DescriptionEditorPaneKt.readHTML(myDescriptionBrowser, description);
      setupPoweredByPanel(actionMetaData);

      showUsages(myBeforePanel, myBeforeUsagePanels, actionMetaData.getExampleUsagesBefore());
      showUsages(myAfterPanel, myAfterUsagePanels, actionMetaData.getExampleUsagesAfter());

      SwingUtilities.invokeLater(() -> myPanel.revalidate());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void setupPoweredByPanel(final IntentionActionMetaData actionMetaData) {
    PluginId pluginId = actionMetaData == null ? null : actionMetaData.getPluginId();
    myPoweredByPanel.removeAll();
    IdeaPluginDescriptorImpl pluginDescriptor  = (IdeaPluginDescriptorImpl)PluginManagerCore.getPlugin(pluginId);
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    boolean isCustomPlugin = pluginDescriptor != null && pluginDescriptor.isBundled() && !appInfo.isEssentialPlugin(pluginId);
    if (isCustomPlugin) {
      HyperlinkLabel label = new HyperlinkLabel(CodeInsightBundle.message("powered.by.plugin", pluginDescriptor.getName()));
      label.addHyperlinkListener(__ -> PluginManagerConfigurable.showPluginConfigurable(ProjectManager.getInstance().getDefaultProject(),
                                                                                        List.of(pluginId)));
      myPoweredByPanel.add(label, BorderLayout.CENTER);
    }
    myPoweredByWrapper.setVisible(isCustomPlugin);
  }


  public void reset(String intentionCategory)  {
    try {
      DescriptionEditorPaneKt.readHTML(myDescriptionBrowser, CodeInsightBundle.message("intention.settings.category.text", intentionCategory));
      setupPoweredByPanel(null);

      TextDescriptor beforeTemplate = new PlainTextDescriptor(CodeInsightBundle.message("templates.intention.settings.category.before"), BEFORE_TEMPLATE);
      showUsages(myBeforePanel, myBeforeUsagePanels, new TextDescriptor[]{beforeTemplate});
      TextDescriptor afterTemplate = new PlainTextDescriptor(CodeInsightBundle.message("templates.intention.settings.category.after"), AFTER_TEMPLATE);
      showUsages(myAfterPanel, myAfterUsagePanels, new TextDescriptor[]{afterTemplate});

      SwingUtilities.invokeLater(() -> myPanel.revalidate());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void showUsages(final JPanel panel,
                                 final List<IntentionUsagePanel> usagePanels,
                                 final TextDescriptor @Nullable [] exampleUsages) throws IOException {
    GridBagConstraints gb = null;
    boolean reuse = exampleUsages != null && panel.getComponents().length == exampleUsages.length;
    if (!reuse) {
      disposeUsagePanels(usagePanels);
      panel.setLayout(new GridBagLayout());
      panel.removeAll();
      gb = new GridBagConstraints();
      gb.anchor = GridBagConstraints.NORTHWEST;
      gb.fill = GridBagConstraints.BOTH;
      gb.gridheight = GridBagConstraints.REMAINDER;
      gb.gridwidth = 1;
      gb.gridx = 0;
      gb.gridy = 0;
      gb.insets = JBUI.emptyInsets();
      gb.ipadx = 5;
      gb.ipady = 5;
      gb.weightx = 1;
      gb.weighty = 1;
    }

    if (exampleUsages != null) {
      for (int i = 0; i < exampleUsages.length; i++) {
        final TextDescriptor exampleUsage = exampleUsages[i];
        final String name = exampleUsage.getFileName();
        final FileTypeManagerEx fileTypeManager = FileTypeManagerEx.getInstanceEx();
        final String extension = fileTypeManager.getExtension(name);
        final FileType fileType = fileTypeManager.getFileTypeByExtension(extension);

        IntentionUsagePanel usagePanel;
        if (reuse) {
          usagePanel = (IntentionUsagePanel)panel.getComponent(i);
        }
        else {
          usagePanel = new IntentionUsagePanel();
          usagePanels.add(usagePanel);
        }
        usagePanel.reset(exampleUsage.getText(), fileType);

        if (!reuse) {
          panel.add(usagePanel, gb);
          gb.gridx++;
        }
      }
    }
    panel.revalidate();
    panel.repaint();
  }

  public JPanel getComponent() {
    return myPanel;
  }

  public void dispose() {
    disposeUsagePanels(myBeforeUsagePanels);
    disposeUsagePanels(myAfterUsagePanels);
  }

  private static void disposeUsagePanels(List<? extends IntentionUsagePanel> usagePanels) {
    for (final IntentionUsagePanel usagePanel : usagePanels) {
      Disposer.dispose(usagePanel);
    }
    usagePanels.clear();
  }

  public void init(final int preferredWidth) {
  }
}