// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane;
import com.intellij.profile.codeInspection.ui.DescriptionEditorPaneKt;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SettingsUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI.PanelFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public final class IntentionDescriptionPanel {
  private static final Logger LOG = Logger.getInstance(IntentionDescriptionPanel.class);
  private final JPanel myPanel;

  private final JPanel myAfterPanel;
  private final JPanel myBeforePanel;
  private final DescriptionEditorPane myDescriptionBrowser;
  private final List<IntentionUsagePanel> myBeforeUsagePanels = new ArrayList<>();
  private final List<IntentionUsagePanel> myAfterUsagePanels = new ArrayList<>();
  private static final @NonNls String BEFORE_TEMPLATE = "before.java.template";
  private static final @NonNls String AFTER_TEMPLATE = "after.java.template";
  private static final float DIVIDER_PROPORTION_DEFAULT = .25f;
  private final @NotNull JPanel myBeforeWrapperPanel;
  private final @NotNull JPanel myAfterWrapperPanel;

  public IntentionDescriptionPanel() {
    myDescriptionBrowser = new DescriptionEditorPane();
    JScrollPane descriptionScrollPane = ScrollPaneFactory.createScrollPane(myDescriptionBrowser);
    descriptionScrollPane.setBorder(null);

    JPanel examplePanel = new JPanel(new GridBagLayout());
    GridBag constraint = new GridBag()
      .setDefaultInsets(UIUtil.LARGE_VGAP, 0, 0, 0)
      .setDefaultFill(GridBagConstraints.BOTH)
      .setDefaultWeightY(0.5)
      .setDefaultWeightX(1.0);

    myBeforePanel = new JPanel();
    myBeforeWrapperPanel = PanelFactory.panel(myBeforePanel)
      .withLabel(CodeInsightBundle.message("border.title.before"))
      .moveLabelOnTop()
      .resizeX(true)
      .resizeY(true)
      .createPanel();
    examplePanel.add(myBeforeWrapperPanel, constraint.nextLine());

    myAfterPanel = new JPanel();
    myAfterWrapperPanel = PanelFactory.panel(myAfterPanel)
      .withLabel(CodeInsightBundle.message("border.title.after"))
      .moveLabelOnTop()
      .resizeX(true)
      .resizeY(true)
      .createPanel();
    examplePanel.add(myAfterWrapperPanel, constraint.nextLine()
    );

    OnePixelSplitter mySplitter = new OnePixelSplitter(true,
                                                       "IntentionDescriptionPanel.VERTICAL_DIVIDER_PROPORTION",
                                                       DIVIDER_PROPORTION_DEFAULT);
    mySplitter.setFirstComponent(descriptionScrollPane);
    mySplitter.setSecondComponent(examplePanel);
    myPanel = mySplitter;

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
                }
                else {
                  Project project = context.getData(CommonDataKeys.PROJECT);
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

  public void reset(IntentionActionMetaData actionMetaData, String filter) {
    try {
      TextDescriptor url = actionMetaData.getDescription();
      String description = StringUtil.isEmpty(url.getText()) ?
                           CodeInsightBundle.message("under.construction.string") :
                           SearchUtil.markup(SettingsUtil.wrapWithPoweredByMessage(url.getText(), actionMetaData.getLoader()), filter);

      DescriptionEditorPaneKt.readHTML(myDescriptionBrowser, description);

      myBeforeWrapperPanel.setVisible(!actionMetaData.isSkipBeforeAfter());
      myAfterWrapperPanel.setVisible(!actionMetaData.isSkipBeforeAfter());
      showUsages(myBeforePanel, myBeforeUsagePanels, actionMetaData.getExampleUsagesBefore());
      showUsages(myAfterPanel, myAfterUsagePanels, actionMetaData.getExampleUsagesAfter());

      SwingUtilities.invokeLater(() -> myPanel.revalidate());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public void reset(String intentionCategory) {
    try {
      DescriptionEditorPaneKt.readHTML(myDescriptionBrowser,
                                       CodeInsightBundle.message("intention.settings.category.text", intentionCategory));

      TextDescriptor beforeTemplate =
        new PlainTextDescriptor(CodeInsightBundle.message("templates.intention.settings.category.before"), BEFORE_TEMPLATE);
      showUsages(myBeforePanel, myBeforeUsagePanels, new TextDescriptor[]{beforeTemplate});
      TextDescriptor afterTemplate =
        new PlainTextDescriptor(CodeInsightBundle.message("templates.intention.settings.category.after"), AFTER_TEMPLATE);
      myBeforeWrapperPanel.setVisible(true);
      myAfterWrapperPanel.setVisible(true);
      showUsages(myAfterPanel, myAfterUsagePanels, new TextDescriptor[]{afterTemplate});

      SwingUtilities.invokeLater(() -> myPanel.revalidate());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void showUsages(JPanel panel,
                                 List<IntentionUsagePanel> usagePanels,
                                 TextDescriptor @Nullable [] exampleUsages) throws IOException {
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
        TextDescriptor exampleUsage = exampleUsages[i];
        String name = exampleUsage.getFileName();
        FileTypeManagerEx fileTypeManager = FileTypeManagerEx.getInstanceEx();
        String extension = fileTypeManager.getExtension(name);
        FileType fileType = fileTypeManager.getFileTypeByExtension(extension);

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
    for (IntentionUsagePanel usagePanel : usagePanels) {
      Disposer.dispose(usagePanel);
    }
    usagePanels.clear();
  }
}