// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.fileTemplates.ui;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.CreateFileAction;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.actions.AttributesDefaults;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.JBInsets;
import org.apache.velocity.runtime.parser.ParseException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;

public class CreateFromTemplateDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(CreateFromTemplateDialog.class);
  @NotNull private final PsiDirectory myDirectory;
  @NotNull private final Project myProject;
  private PsiElement myCreatedElement;
  private final CreateFromTemplatePanel myAttrPanel;
  private final JComponent myAttrComponent;
  @NotNull private final FileTemplate myTemplate;
  private final Properties myDefaultProperties;

  public CreateFromTemplateDialog(@NotNull Project project,
                                  @NotNull PsiDirectory directory,
                                  @NotNull FileTemplate template,
                                  @Nullable final AttributesDefaults attributesDefaults,
                                  @Nullable final Properties defaultProperties) {
    super(project, true);
    myDirectory = directory;
    myProject = project;
    myTemplate = template;
    setTitle(IdeBundle.message("title.new.from.template", template.getName()));

    myDefaultProperties = defaultProperties == null ? FileTemplateManager.getInstance(project).getDefaultProperties() : defaultProperties;
    FileTemplateUtil.fillDefaultProperties(myDefaultProperties, directory);
    boolean mustEnterName = FileTemplateUtil.findHandler(template).isNameRequired();
    if (attributesDefaults != null && attributesDefaults.isFixedName()) {
      myDefaultProperties.setProperty(FileTemplate.ATTRIBUTE_NAME, attributesDefaults.getDefaultFileName());
      mustEnterName = false;
    }
    if (!template.getFileName().isEmpty()) {
      String fileName = FileTemplateUtil.mergeTemplate(myDefaultProperties, template.getFileName(), false);
      try {
        String[] strings = FileTemplateUtil.calculateAttributes(fileName, myDefaultProperties, false, project);
        if (strings.length == 0) {
          myDefaultProperties.setProperty(FileTemplate.ATTRIBUTE_NAME, fileName);
          mustEnterName = false;
        }
      }
      catch (ParseException e) {
        showErrorDialog(e);
      }
    }
    String[] unsetAttributes = null;
    try {
      unsetAttributes = myTemplate.getUnsetAttributes(myDefaultProperties, project);
    }
    catch (ParseException e) {
      showErrorDialog(e);
    }

    if (unsetAttributes != null) {
      myAttrPanel = new CreateFromTemplatePanel(unsetAttributes, mustEnterName, attributesDefaults);
      myAttrComponent = myAttrPanel.getComponent();
      init();
    }
    else {
      myAttrPanel = null;
      myAttrComponent = null;
    }
  }

  public PsiElement create(){
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      doCreate(myTemplate.getName() + "." + myTemplate.getExtension());
      Disposer.dispose(getDisposable());
      return myCreatedElement;
    }
    if (myAttrPanel != null) {
      if (myAttrPanel.hasSomethingToAsk()) {
        show();
        return myCreatedElement;
      }
      doCreate(null);
    }
    close(DialogWrapper.OK_EXIT_CODE);
    return myCreatedElement;
  }

  @Override
  protected void doOKAction(){
    String fileName = myAttrPanel.getFileName();
    if (fileName != null && fileName.length() == 0) {
      Messages.showMessageDialog(myAttrComponent, IdeBundle.message("error.please.enter.a.file.name"), CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return;
    }
    doCreate(fileName);
    if ( myCreatedElement != null ) {
      super.doOKAction();
    }
  }

  private void doCreate(@Nullable String fileName)  {
    try {
      Properties properties = myAttrPanel.getProperties(myDefaultProperties);
      for (FileTemplate child : myTemplate.getChildren()) {
        createFile(child.getFileName(), child, properties);
      }
      String mainFileName = StringUtil.isEmpty(myTemplate.getFileName()) ? fileName : myTemplate.getFileName();
      myCreatedElement = createFile(mainFileName, myTemplate, properties);
    }
    catch (Exception e) {
      showErrorDialog(e);
    }
  }

  private @NotNull PsiElement createFile(@Nullable String fileName,
                                         @NotNull FileTemplate template,
                                         @NotNull Properties properties) throws Exception {
    if (fileName != null) {
      String newName = FileTemplateUtil.mergeTemplate(properties, fileName, false);
      CreateFileAction.MkDirs mkDirs = WriteAction.compute(() -> new CreateFileAction.MkDirs(newName, myDirectory));
      return FileTemplateUtil.createFromTemplate(template, mkDirs.newName, properties, mkDirs.directory);
    }
    return FileTemplateUtil.createFromTemplate(template, null, properties, myDirectory);
  }

  public Properties getEnteredProperties() {
    return myAttrPanel.getProperties(new Properties());
  }

  private void showErrorDialog(final Exception e) {
    LOG.info(e);
    Messages.showMessageDialog(myProject, filterMessage(e.getMessage()), getErrorMessage(), Messages.getErrorIcon());
  }

  private @NlsContexts.DialogTitle String getErrorMessage() {
    return FileTemplateUtil.findHandler(myTemplate).getErrorMessage();
  }

  @Nullable
  private @NlsContexts.DialogMessage String filterMessage(@NlsContexts.DialogMessage String message){
    if (message == null) {
      message = IdeBundle.message("dialog.message.unknown.error");
    }

    @NonNls String ioExceptionPrefix = "java.io.IOException:";
    if (message.startsWith(ioExceptionPrefix)){
      return message.substring(ioExceptionPrefix.length());
    }
    if (message.contains(IdeBundle.message("dialog.message.file.already.exists"))){
      return message;
    }

    return IdeBundle.message("error.unable.to.parse.template.message", myTemplate.getName(), message);
  }

  @Override
  protected JComponent createCenterPanel(){
    myAttrPanel.ensureFitToScreen(200, 200);
    JPanel centerPanel = new JPanel(new GridBagLayout());
    centerPanel.add(myAttrComponent, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                            JBInsets.emptyInsets(), 0, 0));
    return centerPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent(){
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myAttrComponent);
  }
}
