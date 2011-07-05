/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.featureStatistics.actions;

import com.intellij.CommonBundle;
import com.intellij.featureStatistics.*;
import com.intellij.ide.util.TipUIUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

public class ShowFeatureUsageStatisticsDialog extends DialogWrapper {
  private static final Comparator<FeatureDescriptor> DISPLAY_NAME_COMPARATOR = new Comparator<FeatureDescriptor>() {
    public int compare(FeatureDescriptor fd1, FeatureDescriptor fd2) {
      final String displayName1 = fd1.getDisplayName();
      final String displayName2 = fd2.getDisplayName();
      if (displayName1 != null && displayName2 != null) {
        return displayName1.compareTo(displayName2);
      }
      if (displayName2 != null){
        return -1;
      }
      return 1;
    }
  };
  private static final Comparator<FeatureDescriptor> GROUP_NAME_COMPARATOR = new Comparator<FeatureDescriptor>() {
    public int compare(FeatureDescriptor fd1, FeatureDescriptor fd2) {
      return getGroupName(fd1).compareTo(getGroupName(fd2));
    }
  };
  private static final Comparator<FeatureDescriptor> USAGE_COUNT_COMPARATOR = new Comparator<FeatureDescriptor>() {
    public int compare(FeatureDescriptor fd1, FeatureDescriptor fd2) {
      return fd1.getUsageCount() - fd2.getUsageCount();
    }
  };
  private static final Comparator<FeatureDescriptor> LAST_USED_COMPARATOR = new Comparator<FeatureDescriptor>() {
    public int compare(FeatureDescriptor fd1, FeatureDescriptor fd2) {
      return new Date(fd2.getLastTimeUsed()).compareTo(new Date(fd1.getLastTimeUsed()));
    }
  };
  private static final Comparator<FeatureDescriptor> FREQUENCY_COMPARATOR = new Comparator<FeatureDescriptor>() {
    public int compare(FeatureDescriptor fd1, FeatureDescriptor fd2) {
      return new Date(fd1.getAverageFrequency()).compareTo(new Date(fd2.getAverageFrequency()));
    }
  };

  private static final ColumnInfo<FeatureDescriptor, String> DISPLAY_NAME = new ColumnInfo<FeatureDescriptor, String>(FeatureStatisticsBundle.message("feature.statistics.column.feature")) {
    public String valueOf(FeatureDescriptor featureDescriptor) {
      return featureDescriptor.getDisplayName();
    }

    public Comparator<FeatureDescriptor> getComparator() {
      return DISPLAY_NAME_COMPARATOR;
    }
  };
  private static final ColumnInfo<FeatureDescriptor, String> GROUP_NAME = new ColumnInfo<FeatureDescriptor, String>(FeatureStatisticsBundle.message("feature.statistics.column.group")) {
    public String valueOf(FeatureDescriptor featureDescriptor) {
      return getGroupName(featureDescriptor);
    }

    public Comparator<FeatureDescriptor> getComparator() {
      return GROUP_NAME_COMPARATOR;
    }
  };
  private static final ColumnInfo<FeatureDescriptor, String> USED_TOTAL = new ColumnInfo<FeatureDescriptor, String>(FeatureStatisticsBundle.message("feature.statistics.column.usage.count")) {
    public String valueOf(FeatureDescriptor featureDescriptor) {
      int count = featureDescriptor.getUsageCount();
      return FeatureStatisticsBundle.message("feature.statistics.usage.count", count);
    }

    public Comparator<FeatureDescriptor> getComparator() {
      return USAGE_COUNT_COMPARATOR;
    }
  };
  private static final ColumnInfo<FeatureDescriptor, String> LAST_USED = new ColumnInfo<FeatureDescriptor, String>(FeatureStatisticsBundle.message("feature.statistics.column.last.used")) {
    public String valueOf(FeatureDescriptor featureDescriptor) {
      long tm = featureDescriptor.getLastTimeUsed();
      if (tm <= 0) return FeatureStatisticsBundle.message("feature.statistics.not.applicable");
      return DateFormatUtil.formatBetweenDates(tm, System.currentTimeMillis());
    }

    public Comparator<FeatureDescriptor> getComparator() {
      return LAST_USED_COMPARATOR;
    }
  };
  private static final ColumnInfo<FeatureDescriptor, String> USAGE_FREQUENCY = new ColumnInfo<FeatureDescriptor, String>(FeatureStatisticsBundle.message("feature.statistics.column.usage.frequency")) {
    public String valueOf(FeatureDescriptor featureDescriptor) {
      long tm = featureDescriptor.getAverageFrequency();
      if (tm <= 0) return FeatureStatisticsBundle.message("feature.statistics.not.applicable");
      return DateFormatUtil.formatFrequency(tm);
    }

    public Comparator<FeatureDescriptor> getComparator() {
      return FREQUENCY_COMPARATOR;
    }
  };

  private static final ColumnInfo[] COLUMNS = new ColumnInfo[]{DISPLAY_NAME, GROUP_NAME, USED_TOTAL, LAST_USED, USAGE_FREQUENCY};
  private final Project myProject;

  public ShowFeatureUsageStatisticsDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(FeatureStatisticsBundle.message("feature.statistics.dialog.title"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    setModal(false);
    init();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.featureStatistics.actions.ShowFeatureUsageStatisticsDialog";
  }

  protected Action[] createActions() {
    return new Action[] {getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("editing.productivityGuide");
  }

  protected JComponent createCenterPanel() {
    Splitter splitter = new Splitter(true);
    splitter.setShowDividerControls(true);

    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    Set<String> ids = registry.getFeatureIds();
    ArrayList<FeatureDescriptor> features = new ArrayList<FeatureDescriptor>();
    for (Iterator<String> iterator = ids.iterator(); iterator.hasNext();) {
      String id = iterator.next();
      features.add(registry.getFeatureDescriptor(id));
    }
    final TableView table = new TableView(new ListTableModel(COLUMNS, features, 0));

    JPanel controlsPanel = new JPanel(new VerticalFlowLayout());


    Application app = ApplicationManager.getApplication();
    long uptime = System.currentTimeMillis() - app.getStartTime();
    long idletime = app.getIdleTime();

    final String uptimeS = FeatureStatisticsBundle.message("feature.statistics.application.uptime",
                                                           ApplicationNamesInfo.getInstance().getProductName(),
                                                           DateFormatUtil.formatDuration(uptime));

    final String idleTimeS = FeatureStatisticsBundle .message("feature.statistics.application.idle.time",
                                                              ApplicationNamesInfo.getInstance().getProductName(),
                                                              DateFormatUtil.formatDuration(idletime));

    controlsPanel.add(new JLabel(uptimeS + ", " + idleTimeS), BorderLayout.NORTH);

    final JCheckBox compiler = new JCheckBox(FeatureStatisticsBundle.message("feature.statistics.show.while.compiling"));
    compiler.setSelected(FeatureUsageTracker.getInstance().SHOW_IN_COMPILATION_PROGRESS);
    compiler.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FeatureUsageTracker.getInstance().SHOW_IN_COMPILATION_PROGRESS = compiler.isSelected();
      }
    });
    compiler.setVisible(haveCompilerExtensionPoint());

    final JCheckBox other = new JCheckBox(FeatureStatisticsBundle.message("feature.statistics.show.on.startup"));
    other.setSelected(FeatureUsageTracker.getInstance().SHOW_IN_OTHER_PROGRESS);
    other.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FeatureUsageTracker.getInstance().SHOW_IN_OTHER_PROGRESS = other.isSelected();
      }
    });

    controlsPanel.add(compiler);
    controlsPanel.add(other);

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.add(controlsPanel, BorderLayout.NORTH);
    topPanel.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);

    splitter.setFirstComponent(topPanel);

    //noinspection HardCodedStringLiteral
    final JEditorPane browser = new JEditorPane("text/html", "");
    browser.setEditable(false);
    splitter.setSecondComponent(ScrollPaneFactory.createScrollPane(browser));

    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        Collection selection = table.getSelection();
        try {
          if (selection.isEmpty()) {
            browser.read(new StringReader(""), null);
          }
          else {
            FeatureDescriptor feature = (FeatureDescriptor)selection.iterator().next();
            TipUIUtil.openTipInBrowser(feature.getTipFileName(), browser, feature.getProvider());
          }
        }
        catch (IOException ex) {
        }
      }
    });

    return splitter;
  }

  private boolean haveCompilerExtensionPoint() {
    final ExtensionPoint[] extensionPoints = Extensions.getArea(myProject).getExtensionPoints();
    for (ExtensionPoint extensionPoint : extensionPoints) {
      if (extensionPoint.getName().equals("com.intellij.compiler")) {
        return true;
      }
    }
    return false;
  }

  private static String getGroupName(FeatureDescriptor featureDescriptor) {
    final ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();    
    final GroupDescriptor groupDescriptor = registry.getGroupDescriptor(featureDescriptor.getGroupId());
    return groupDescriptor != null ? groupDescriptor.getDisplayName() : "";
  }
}