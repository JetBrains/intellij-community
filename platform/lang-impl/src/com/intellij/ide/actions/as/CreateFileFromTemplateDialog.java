/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions.as;

import com.intellij.ide.actions.ElementCreator;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.as.CreateNewClassDialogValidatorEx;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateFileFromTemplateDialog extends DialogWrapper {
  private JPanel myPanel;
  private JLabel myNameLabel;
  private JTextField myNameField;
  private JLabel myUpDownHint;
  private JLabel myKindLabel;
  private TemplateKindCombo myKindCombo;
  private JLabel mySuperclassLabel;
  private JTextField mySuperclassField;
  private JLabel myInterfacesLabel;
  private JTextField myInterfacesField;
  private JLabel myPackageLabel;
  private JTextField myPackageField;
  private JLabel myVisibilityLabel;
  private ButtonGroup myVisibilityButtonGroup;
  private JRadioButton myPublicRadioButton;
  private JRadioButton myPackagePrivateRadioButton;
  private JCheckBox myAbstractCheckBox;
  private JCheckBox myFinalCheckBox;
  private JCheckBox myShowSelectOverridesDialogCheckBox;

  private ElementCreator myCreator;
  private CreateNewClassDialogValidatorEx myInputValidator;

  private static final String VISIBILITY_PACKAGE_PRIVATE = "visibility_package_private";
  private static final String VISIBILITY_PUBLIC = "visibility_public";

  protected CreateFileFromTemplateDialog(@NotNull Project project) {
    super(project);

    myKindLabel.setLabelFor(myKindCombo);
    myVisibilityLabel.setLabelFor(myPublicRadioButton);
    myKindCombo.registerUpDownHint(myNameField);
    myUpDownHint.setIcon(PlatformIcons.UP_DOWN_ARROWS);
    setKindComponentsVisible(false);
    initVisibilityButtons();

    myKindCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (actionEvent.getSource().equals(myKindCombo.getComboBox())) {
          configureComponents(Kind.valueOfText(myKindCombo.getSelectedName()));
        }
      }
    });

    init();
  }

  private void initVisibilityButtons() {
    myPublicRadioButton.setActionCommand(VISIBILITY_PUBLIC);
    myPackagePrivateRadioButton.setActionCommand(VISIBILITY_PACKAGE_PRIVATE);

    myVisibilityButtonGroup = new ButtonGroup();
    myVisibilityButtonGroup.add(myPublicRadioButton);
    myVisibilityButtonGroup.add(myPackagePrivateRadioButton);
    myPublicRadioButton.setSelected(true);
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (myInputValidator != null) {
      String nameText = myNameField.getText();
      String superclassText = mySuperclassField.getText();
      String interfacesText = myInterfacesField.getText();
      String packageText = myPackageField.getText();
      if (!myInputValidator.checkInput(nameText)) {
        String errorText = LangBundle.message("incorrect.name");
        String message = myInputValidator.getErrorText(nameText);
        if (message != null) {
          errorText = message;
        }

        return new ValidationInfo(errorText, myNameField);
      }
      else if (!myInputValidator.checkInterfaces(interfacesText)) {
        String errorText = LangBundle.message("incorrect.interfaces");
        String message = myInputValidator.getInterfacesErrorText(interfacesText);
        if (message != null) {
          errorText = message;
        }

        return new ValidationInfo(errorText, myInterfacesField);
      }
      else if (!myInputValidator.checkPackage(packageText)) {
        String errorText = LangBundle.message("incorrect.package");
        String message = myInputValidator.getPackageErrorText(packageText);
        if (message != null) {
          errorText = message;
        }

        return new ValidationInfo(errorText, myPackageField);
      }
      else if (mySuperclassField.isVisible() && !myInputValidator.checkSuperclass(superclassText)) {
        String errorText = LangBundle.message("incorrect.superclass");
        String message = myInputValidator.getSuperclassErrorText(superclassText);
        if (message != null) {
          errorText = message;
        }

        return new ValidationInfo(errorText, mySuperclassField);
      }
      else if (myAbstractCheckBox.isVisible() && myFinalCheckBox.isVisible() &&
               !myInputValidator.checkAbstractAndFinal(myAbstractCheckBox.isSelected(), myFinalCheckBox.isSelected())) {
        String errorText = LangBundle.message("incorrect.abstract.and.final");
        String message = myInputValidator.getAbstractAndFinalErrorText("");
        if (message != null) {
          errorText = message;
        }

        return new ValidationInfo(errorText, myFinalCheckBox);
      }
    }
    return super.doValidate();
  }

  protected void configureComponents(Kind type) {
    switch (type) {
      case ANNOTATION:
      case INTERFACE:
      case ENUM:
        mySuperclassLabel.setVisible(false);
        mySuperclassField.setVisible(false);
        mySuperclassField.setText("");
        myAbstractCheckBox.setVisible(false);
        myAbstractCheckBox.setSelected(false);
        myFinalCheckBox.setVisible(false);
        myFinalCheckBox.setSelected(false);
        myShowSelectOverridesDialogCheckBox.setVisible(false);
        myShowSelectOverridesDialogCheckBox.setSelected(false);
        break;
      case CLASS:
      case SINGLETON:
        mySuperclassLabel.setVisible(true);
        mySuperclassField.setVisible(true);
        myAbstractCheckBox.setVisible(true);
        myFinalCheckBox.setVisible(true);
        myShowSelectOverridesDialogCheckBox.setVisible(true);
        break;
    }
  }

  protected JTextField getNameField() {
    return myNameField;
  }

  protected TemplateKindCombo getKindCombo() {
    return myKindCombo;
  }

  protected JLabel getKindLabel() {
    return myKindLabel;
  }

  protected JLabel getNameLabel() {
    return myNameLabel;
  }

  private String getName() {
    String text = myNameField.getText().trim();
    myNameField.setText(text);
    return text;
  }

  public String getSuperclass() {
    String superclass = mySuperclassField.getText().trim();
    mySuperclassField.setText(superclass);
    return superclass;
  }

  public void setSuperclass(String superclass) {
    mySuperclassField.setText(superclass);
  }

  private String getPackage() {
    String packageName = myPackageField.getText().replace(" ", "");
    myPackageField.setText(packageName);
    return packageName;
  }

  public void setPackage(String packageName) {
    myPackageField.setText(packageName);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    Map<String, String> creationOptions = new HashMap<String, String>();
    creationOptions.put(FileTemplate.ATTRIBUTE_PACKAGE_NAME, getPackage());
    Visibility visibility = myPublicRadioButton.isSelected() ? Visibility.PUBLIC : Visibility.PACKAGE_PRIVATE;
    creationOptions.put(FileTemplate.ATTRIBUTE_VISIBILITY, visibility.toString());
    creationOptions.put(FileTemplate.ATTRIBUTE_INTERFACES, getInterfaces());
    creationOptions.put(FileTemplate.ATTRIBUTE_SUPERCLASS, getSuperclass());
    creationOptions.put(FileTemplate.ATTRIBUTE_ABSTRACT, Boolean.toString(myAbstractCheckBox.isSelected()).toUpperCase(Locale.ROOT));
    creationOptions.put(FileTemplate.ATTRIBUTE_FINAL, Boolean.toString(myFinalCheckBox.isSelected()).toUpperCase(Locale.ROOT));
    if (myCreator != null && myCreator.tryCreate(getName(), creationOptions).length == 0) {
      return;
    }

    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return getNameField();
  }

  public void setKindComponentsVisible(boolean visible) {
    myKindCombo.setVisible(visible);
    myKindLabel.setVisible(visible);
    myUpDownHint.setVisible(visible);
  }

  public static Builder newBuilder(@NotNull Project project) {
    return new BuilderImpl(new CreateFileFromTemplateDialog(project), project);
  }

  public String getInterfaces() {
    return myInterfacesField.getText();
  }

  public void setInterfaces(String interfaces) {
    myInterfacesField.setText(interfaces);
  }

  private static class BuilderImpl implements Builder {
    private CreateFileFromTemplateDialog myDialog;
    private Project myProject;

    private BuilderImpl(CreateFileFromTemplateDialog dialog, Project project) {
      myDialog = dialog;
      myProject = project;
    }

    @Override
    public Builder setTitle(String title) {
      myDialog.setTitle(title);
      return this;
    }

    @Override
    public Builder addKind(@NotNull String name, @Nullable Icon icon, @NotNull String templateName) {
      myDialog.getKindCombo().addItem(name, icon, templateName);
      if (myDialog.getKindCombo().getComboBox().getItemCount() > 1) {
        myDialog.setKindComponentsVisible(true);
      }
      return this;
    }

    @Override
    public Builder setValidator(InputValidator validator) {
      if (validator instanceof CreateNewClassDialogValidatorEx) {
        myDialog.myInputValidator = (CreateNewClassDialogValidatorEx)validator;
        return this;
      }
      else {
        throw new IllegalArgumentException("Validator must be of type CreateNewClassDialogValidatorEx");
      }
    }

    @Override
    public Builder setPackage(String packageName) {
      myDialog.setPackage(packageName);
      return this;
    }

    @Override
    public <T extends PsiElement> T show(@NotNull String errorTitle, @Nullable final String selectedTemplateName,
                                         @NotNull final com.intellij.ide.actions.CreateFileFromTemplateDialog.FileCreator<T> creator) {
      final Ref<T> ref = Ref.create(null);
      myDialog.getKindCombo().setSelectedName(selectedTemplateName);
      myDialog.myCreator = new ElementCreator(myProject, errorTitle) {

        @Override
        protected PsiElement[] create(String newName, Map<String, String> creationOptions) throws Exception {
          T element;
          if (selectedTemplateName == null) {
            element = creator.createFile(myDialog.getName(), creationOptions, myDialog.getKindCombo().getSelectedName());
          }
          else {
            element = creator.createFile(myDialog.getName(), creationOptions, selectedTemplateName);
          }
          ref.set(element);
          return element == null ? PsiElement.EMPTY_ARRAY : new PsiElement[]{element};
        }

        @Override
        protected String getActionName(String newName) {
          return creator.getActionName(newName, myDialog.getKindCombo().getSelectedName());
        }
      };

      myDialog.show();
      return myDialog.getExitCode() == OK_EXIT_CODE ? ref.get() : null;
    }

    @Nullable
    @Override
    public Map<String, String> getCustomProperties() {
      return Collections.emptyMap();
    }
  }

  public interface Builder extends com.intellij.ide.actions.CreateFileFromTemplateDialog.Builder {
    Builder setPackage(String packageName);
  }

  public enum Visibility {
    PUBLIC,
    PACKAGE_PRIVATE
  }

  private enum Kind {
    ANNOTATION("AnnotationType"),
    CLASS("Class"),
    ENUM("Enum"),
    INTERFACE("Interface"),
    SINGLETON("Singleton");

    private final String myText;

    Kind(String text) {
      myText = text;
    }

    @Override
    public String toString() {
      return myText;
    }

    private static Kind valueOfText(String text) {
      for (Kind kind : values()) {
        if (kind.toString().equals(text)) {
          return kind;
        }
      }

      throw new IllegalArgumentException(text);
    }
  }
}
