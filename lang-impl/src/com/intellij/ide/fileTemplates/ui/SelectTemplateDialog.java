package com.intellij.ide.fileTemplates.ui;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.psi.PsiDirectory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
/*
 * @author: MYakovlev
 * Date: Aug 22, 2002
 * Time: 1:31:43 PM
 */
public class SelectTemplateDialog extends DialogWrapper{
  private JComboBox myCbxTemplates;
  private FileTemplate mySelectedTemplate;
  private Project myProject;
  private PsiDirectory myDirectory;

  public SelectTemplateDialog(Project project, PsiDirectory directory){
    super(project, true);
    myDirectory = directory;
    myProject = project;
    setTitle(IdeBundle.message("title.select.template"));
    init();
  }

  protected JComponent createCenterPanel(){
    loadCombo();

    JButton editTemplatesButton = new FixedSizeButton(myCbxTemplates);

    JPanel centerPanel = new JPanel(new GridBagLayout());
    JLabel selectTemplateLabel = new JLabel(IdeBundle.message("label.name"));

    centerPanel.add(selectTemplateLabel,       new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(2, 2, 2, 2), 0, 0));
    centerPanel.add(myCbxTemplates,       new GridBagConstraints(1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2, 2), 50, 0));
    centerPanel.add(editTemplatesButton,       new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(2, 2, 2, 2), 0, 0));

    editTemplatesButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        onEditTemplates();
      }
    });

    return centerPanel;
  }

  private void loadCombo(){
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    FileTemplate[] allTemplates = FileTemplateManager.getInstance().getAllTemplates();
    PsiDirectory[] dirs = {myDirectory};
    for (FileTemplate template : allTemplates) {
      if (FileTemplateUtil.canCreateFromTemplate(dirs, template)) {
        model.addElement(template);
      }
    }
    if(myCbxTemplates == null){
      myCbxTemplates = new JComboBox(model);
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

  protected void doOKAction(){
    mySelectedTemplate = (FileTemplate)myCbxTemplates.getSelectedItem();
    super.doOKAction();
  }

  public void doCancelAction(){
    mySelectedTemplate = null;
    super.doCancelAction();
  }

  public JComponent getPreferredFocusedComponent(){
    return myCbxTemplates;
  }

  private void onEditTemplates(){
    new ConfigureTemplatesDialog(myProject).show();
    loadCombo();
  }

}
