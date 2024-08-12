// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates;

import com.intellij.CommonBundle;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Arrays;

/**
 * @author Dmitry Avdeev
 */
final class ManageProjectTemplatesDialog extends DialogWrapper {

  private final JPanel myPanel;
  private final JBList<ProjectTemplate> myTemplatesList;
  private final JTextPane myDescriptionPane;

  ManageProjectTemplatesDialog() {
    super(false);
    setTitle(LangBundle.message("dialog.title.manage.project.templates"));
    final ProjectTemplate[] templates =
      new ArchivedTemplatesFactory().createTemplates(ProjectTemplatesFactory.CUSTOM_GROUP, new WizardContext(null, getDisposable()));
    myTemplatesList = new JBList<>(new CollectionListModel<>(Arrays.asList(templates)) {
      @Override
      public void remove(int index) {
        ProjectTemplate template = getElementAt(index);
        super.remove(index);
        if (template instanceof LocalArchivedTemplate) {
          FileUtil.delete(new File(((LocalArchivedTemplate)template).getArchivePath().getPath()));
        }
      }
    });
    myTemplatesList.setEmptyText(LangBundle.message("status.text.no.user.defined.project.templates"));
    myTemplatesList.setCellRenderer(BuilderKt.textListCellRenderer(ProjectTemplate::getName));
    myTemplatesList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        ProjectTemplate template = getSelectedTemplate();
        myDescriptionPane.setText(template == null ? null : template.getDescription());
      }
    });

    myPanel = new JPanel(new BorderLayout(0, 5));
    JPanel panel = ToolbarDecorator.createDecorator(myTemplatesList).disableUpDownActions().createPanel();
    panel.setPreferredSize(JBUI.size(300, 200));
    myPanel.add(panel);

    myDescriptionPane = new JTextPane();
    myDescriptionPane.setPreferredSize(JBUI.size(300, 50));
    Messages.installHyperlinkSupport(myDescriptionPane);
    myPanel.add(ScrollPaneFactory.createScrollPane(myDescriptionPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                   ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.SOUTH);

    if (templates.length > 0) {
      myTemplatesList.setSelectedValue(templates[0], true);
    }

    init();
  }

  private @Nullable ProjectTemplate getSelectedTemplate() {
    return myTemplatesList.getSelectedValue();
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{ new DialogWrapperAction(CommonBundle.getCloseButtonText()) {
      @Override
      protected void doAction(ActionEvent e) {
        doCancelAction();
      }
    }};
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myTemplatesList;
  }
}
