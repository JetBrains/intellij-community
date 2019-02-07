// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.actions;

import com.intellij.CommonBundle;
import com.intellij.featureStatistics.*;
import com.intellij.ide.util.TipUIUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableViewSpeedSearch;
import com.intellij.ui.table.TableView;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;

public class ShowFeatureUsageStatisticsDialog extends DialogWrapper {
  private static final Comparator<FeatureDescriptor> DISPLAY_NAME_COMPARATOR = Comparator.comparing(FeatureDescriptor::getDisplayName);
  private static final Comparator<FeatureDescriptor> GROUP_NAME_COMPARATOR = Comparator.comparing(ShowFeatureUsageStatisticsDialog::getGroupName);
  private static final Comparator<FeatureDescriptor> USAGE_COUNT_COMPARATOR = Comparator.comparingInt(FeatureDescriptor::getUsageCount);
  private static final Comparator<FeatureDescriptor> LAST_USED_COMPARATOR =
    (fd1, fd2) -> new Date(fd2.getLastTimeUsed()).compareTo(new Date(fd1.getLastTimeUsed()));

  private static final ColumnInfo<FeatureDescriptor, String> DISPLAY_NAME = new ColumnInfo<FeatureDescriptor, String>(FeatureStatisticsBundle.message("feature.statistics.column.feature")) {
    @Override
    public String valueOf(FeatureDescriptor featureDescriptor) {
      return featureDescriptor.getDisplayName();
    }

    @Override
    public Comparator<FeatureDescriptor> getComparator() {
      return DISPLAY_NAME_COMPARATOR;
    }
  };
  private static final ColumnInfo<FeatureDescriptor, String> GROUP_NAME = new ColumnInfo<FeatureDescriptor, String>(FeatureStatisticsBundle.message("feature.statistics.column.group")) {
    @Override
    public String valueOf(FeatureDescriptor featureDescriptor) {
      return getGroupName(featureDescriptor);
    }

    @Override
    public Comparator<FeatureDescriptor> getComparator() {
      return GROUP_NAME_COMPARATOR;
    }
  };
  private static final ColumnInfo<FeatureDescriptor, String> USED_TOTAL = new ColumnInfo<FeatureDescriptor, String>(FeatureStatisticsBundle.message("feature.statistics.column.usage.count")) {
    @Override
    public String valueOf(FeatureDescriptor featureDescriptor) {
      int count = featureDescriptor.getUsageCount();
      return FeatureStatisticsBundle.message("feature.statistics.usage.count", count);
    }

    @Override
    public Comparator<FeatureDescriptor> getComparator() {
      return USAGE_COUNT_COMPARATOR;
    }
  };
  private static final ColumnInfo<FeatureDescriptor, String> LAST_USED = new ColumnInfo<FeatureDescriptor, String>(FeatureStatisticsBundle.message("feature.statistics.column.last.used")) {
    @Override
    public String valueOf(FeatureDescriptor featureDescriptor) {
      long tm = featureDescriptor.getLastTimeUsed();
      if (tm <= 0) return FeatureStatisticsBundle.message("feature.statistics.not.applicable");
      return DateFormatUtil.formatBetweenDates(tm, System.currentTimeMillis());
    }

    @Override
    public Comparator<FeatureDescriptor> getComparator() {
      return LAST_USED_COMPARATOR;
    }
  };

  private static final ColumnInfo[] COLUMNS = new ColumnInfo[]{DISPLAY_NAME, GROUP_NAME, USED_TOTAL, LAST_USED};

  public ShowFeatureUsageStatisticsDialog(Project project) {
    super(project, true);
    setTitle(FeatureStatisticsBundle.message("feature.statistics.dialog.title"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    setModal(false);
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.featureStatistics.actions.ShowFeatureUsageStatisticsDialog";
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getCancelAction(), getHelpAction()};
  }

  @Override
  protected String getHelpId() {
    return "editing.productivityGuide";
  }

  @Override
  protected JComponent createCenterPanel() {
    Splitter splitter = new Splitter(true);
    splitter.setShowDividerControls(true);

    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    ArrayList<FeatureDescriptor> features = new ArrayList<>();
    for (String id : registry.getFeatureIds()) {
      features.add(registry.getFeatureDescriptor(id));
    }
    TableView<FeatureDescriptor> table = new TableView<>(new ListTableModel<>(COLUMNS, features, 0));
    new TableViewSpeedSearch<FeatureDescriptor>(table) {
      @Override
      protected String getItemText(@NotNull FeatureDescriptor element) {
        return element.getDisplayName();
      }
    };

    JPanel controlsPanel = new JPanel(new VerticalFlowLayout());


    Application app = ApplicationManager.getApplication();
    long uptime = System.currentTimeMillis() - app.getStartTime();
    long idleTime = app.getIdleTime();

    final String uptimeS = FeatureStatisticsBundle.message("feature.statistics.application.uptime",
                                                           ApplicationNamesInfo.getInstance().getFullProductName(),
                                                           DateFormatUtil.formatDuration(uptime));

    final String idleTimeS = FeatureStatisticsBundle.message("feature.statistics.application.idle.time",
                                                             DateFormatUtil.formatDuration(idleTime));

    String labelText = uptimeS + ", " + idleTimeS;
    CompletionStatistics stats = ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getCompletionStatistics();
    if (stats.dayCount > 0 && stats.sparedCharacters > 0) {
      String total = formatCharacterCount(stats.sparedCharacters, true);
      String perDay = formatCharacterCount(stats.sparedCharacters / stats.dayCount, false);
      labelText += "<br>Code completion has saved you from typing at least " + total + " since " + DateFormatUtil.formatDate(stats.startDate) +
                   " (~" + perDay + " per working day)";
    }

    CumulativeStatistics fstats = ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getFixesStats();
    if (fstats.dayCount > 0 && fstats.invocations > 0) {
      labelText +=
        "<br>Quick fixes have saved you from " + fstats.invocations + " possible bugs since " + DateFormatUtil.formatDate(fstats.startDate) +
        " (~" + fstats.invocations / fstats.dayCount + " per working day)";
    }

    controlsPanel.add(new JLabel(XmlStringUtil.wrapInHtml(labelText)), BorderLayout.NORTH);

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.add(controlsPanel, BorderLayout.NORTH);
    topPanel.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);

    splitter.setFirstComponent(topPanel);

    final TipUIUtil.Browser browser = TipUIUtil.createBrowser();
    splitter.setSecondComponent(ScrollPaneFactory.createScrollPane(browser.getComponent()));

    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        Collection selection = table.getSelection();
        if (selection.isEmpty()) {
          browser.setText("");
        }
        else {
          FeatureDescriptor feature = (FeatureDescriptor)selection.iterator().next();
          TipUIUtil.openTipInBrowser(TipUIUtil.getTip(feature.getTipFileName()), browser);
        }
      }
    });

    return splitter;
  }

  private static String formatCharacterCount(int count, boolean full) {
    DecimalFormat oneDigit = new DecimalFormat("0.0");
    String result = count > 1024 * 1024 ? oneDigit.format((double)count / 1024 / 1024) + "M" :
               count > 1024 ? oneDigit.format((double)count / 1024) + "K" :
               String.valueOf(count);
    if (full) {
      return result + " characters";
    }
    return result;
  }

  private static String getGroupName(FeatureDescriptor featureDescriptor) {
    final ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    final GroupDescriptor groupDescriptor = registry.getGroupDescriptor(featureDescriptor.getGroupId());
    return groupDescriptor != null ? groupDescriptor.getDisplayName() : "";
  }
}
