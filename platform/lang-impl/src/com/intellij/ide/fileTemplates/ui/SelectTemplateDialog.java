// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.fileTemplates.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
/*
 * @author MYakovlev
 */
@ApiStatus.Internal
public final class SelectTemplateDialog extends DialogWrapper{
  private ComboBox<FileTemplate> myCbxTemplates;
  private FileTemplate mySelectedTemplate;
  private final Project myProject;
  private final PsiDirectory myDirectory;

  public SelectTemplateDialog(Project project, PsiDirectory directory){
    super(project, true);
    myDirectory = directory;
    myProject = project;
    setTitle(IdeBundle.message("title.select.template"));
    init();
  }

  @Override
  protected JComponent createCenterPanel(){
    loadCombo();

    JButton editTemplatesButton = new FixedSizeButton(myCbxTemplates);

    JPanel centerPanel = new JPanel(new GridBagLayout());
    JLabel selectTemplateLabel = new JLabel(IdeBundle.message("label.name"));

    centerPanel.add(selectTemplateLabel, new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, JBUI.insets(2), 0, 0));
    centerPanel.add(myCbxTemplates, new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL, JBUI.insets(2), 50, 0));
    centerPanel.add(editTemplatesButton, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, JBUI.insets(2), 0, 0));

    editTemplatesButton.addActionListener(new ActionListener(){
      @Override
      public void actionPerformed(ActionEvent e){
        onEditTemplates();
      }
    });

    return centerPanel;
  }

  private void loadCombo(){
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    FileTemplate[] allTemplates = FileTemplateManager.getInstance(myProject).getAllTemplates();
    PsiDirectory[] dirs = {myDirectory};
    for (FileTemplate template : allTemplates) {
      if (FileTemplateUtil.canCreateFromTemplate(dirs, template)) {
        model.addElement(template);
      }
    }
    if(myCbxTemplates == null){
      myCbxTemplates = new ComboBox(model);
      ComboboxSpeedSearch search = new ComboboxSpeedSearch(myCbxTemplates, null) {
        @Override
        protected String getElementText(Object element) {
          return element instanceof FileTemplate ? ((FileTemplate)element).getName() : null;
        }
      };
      search.setupListeners();
      myCbxTemplates.setRenderer(SimpleListCellRenderer.create((label, fileTemplate, index) -> {
        if (fileTemplate != null) {
          label.setIcon(FileTemplateUtil.getIcon(fileTemplate));
          label.setText(fileTemplate.getName());
        }
      }));
    }
    else{
      Object selected = myCbxTemplates.getSelectedItem();
      myCbxTemplates.setModel(model);
      if(selected != null){
        myCbxTemplates.setSelectedItem(selected);
      }
    }
  }

  public FileTemplate getSelectedTemplate(){
    return mySelectedTemplate;
  }

  @Override
  protected void doOKAction(){
    mySelectedTemplate = (FileTemplate)myCbxTemplates.getSelectedItem();
    super.doOKAction();
  }

  @Override
  public void doCancelAction(){
    mySelectedTemplate = null;
    super.doCancelAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent(){
    return myCbxTemplates;
  }

  private void onEditTemplates(){
    new ConfigureTemplatesDialog(myProject).show();
    loadCombo();
  }

}
