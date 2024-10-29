// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.ide.actions.newclass.CreateWithTemplatesDialogPanel;
import com.intellij.ide.actions.newclass.CreateWithTemplatesDialogPanel.TemplatePresentation;
import com.intellij.ide.ui.newItemPopup.NewItemPopupUtil;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.util.NlsContexts.DialogTitle;

public class CreateFileFromTemplateDialog extends DialogWrapper {
  private JTextField myNameField;
  private TemplateKindCombo myKindCombo;
  private JPanel myPanel;
  private JLabel myUpDownHint;
  private JLabel myKindLabel;
  private JLabel myNameLabel;

  private ElementCreator myCreator;
  private InputValidator myInputValidator;
  private final Map<String, InputValidator> myExtraValidators = new HashMap<>();

  protected CreateFileFromTemplateDialog(@NotNull Project project) {
    super(project, true);

    myKindLabel.setLabelFor(myKindCombo);
    myKindCombo.registerUpDownHint(myNameField);
    myUpDownHint.setIcon(PlatformIcons.UP_DOWN_ARROWS);
    setTemplateKindComponentsVisible(false);
    init();
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    final String text = myNameField.getText().trim();
    InputValidator[] validators = {myInputValidator, myExtraValidators.get(getKindCombo().getSelectedName())};
    for (InputValidator validator : validators) {
      if (validator != null) {
        final boolean canClose = validator.canClose(text);
        if (!canClose) {
          String errorText = LangBundle.message("incorrect.name");
          if (validator instanceof InputValidatorEx) {
            String message = ((InputValidatorEx)validator).getErrorText(text);
            if (message != null) {
              errorText = message;
            }
          }
          return new ValidationInfo(errorText, myNameField);
        }
      }
    }
    return super.doValidate();
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

  private String getEnteredName() {
    final JTextField nameField = getNameField();
    final String text = nameField.getText().trim();
    nameField.setText(text);
    return text;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    if (myCreator != null && myCreator.tryCreate(getEnteredName()).length == 0) {
      return;
    }
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return getNameField();
  }

  public void setTemplateKindComponentsVisible(boolean flag) {
    myKindCombo.setVisible(flag);
    myKindLabel.setVisible(flag);
    myUpDownHint.setVisible(flag);
  }

  public static Builder createDialog(final @NotNull Project project) {
    if (Experiments.getInstance().isFeatureEnabled("show.create.new.element.in.popup")) {
     return new NonBlockingPopupBuilderImpl(project);
    }
    else {
      final CreateFileFromTemplateDialog dialog = new CreateFileFromTemplateDialog(project);
      return new BuilderImpl(dialog, project);
    }
  }

  private static final class BuilderImpl implements Builder {
    private final CreateFileFromTemplateDialog myDialog;
    private final Project myProject;

    BuilderImpl(CreateFileFromTemplateDialog dialog, Project project) {
      myDialog = dialog;
      myProject = project;
    }

    @Override
    public Builder setTitle(@DialogTitle String title) {
      myDialog.setTitle(title);
      return this;
    }

    @Override
    public Builder setDefaultText(String text) {
      JTextField nameField = myDialog.getNameField();
      nameField.setText(text);
      nameField.selectAll();
      return this;
    }

    @Override
    public Builder addKind(@Nls @NotNull String name, @Nullable Icon icon, @NotNull String templateName,
                           @Nullable InputValidator extraValidator) {
      myDialog.getKindCombo().addItem(name, icon, templateName);
      if (extraValidator != null) {
        myDialog.myExtraValidators.put(templateName, extraValidator);
      }
      if (myDialog.getKindCombo().getComboBox().getItemCount() > 1) {
        myDialog.setTemplateKindComponentsVisible(true);
      }
      return this;
    }

    @Override
    public Builder setValidator(InputValidator validator) {
      myDialog.myInputValidator = validator;
      return this;
    }

    @Override
    public Builder setDialogOwner(@Nullable Component owner) {
      throw new UnsupportedOperationException("Dialog owner is supposed to be baked in CreateFileFromTemplateDialog passed via constructor");
    }

    @Override
    public <T extends PsiElement> T show(@NotNull String errorTitle, @Nullable String selectedTemplateName,
                                         final @NotNull FileCreator<T> creator) {
      final Ref<SmartPsiElementPointer<T>> created = Ref.create(null);
      myDialog.getKindCombo().setSelectedName(selectedTemplateName);
      myDialog.myCreator = new ElementCreator(myProject, errorTitle) {

        @Override
        protected PsiElement @NotNull [] create(@NotNull String newName) {
          T element = creator.createFile(myDialog.getEnteredName(), myDialog.getKindCombo().getSelectedName());
          if (element != null) {
            created.set(SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element));
            return new PsiElement[]{element};
          }
          return PsiElement.EMPTY_ARRAY;
        }

        @Override
        public boolean startInWriteAction() {
          return creator.startInWriteAction();
        }

        @Override
        protected @NotNull String getActionName(@NotNull String newName) {
          return creator.getActionName(newName, myDialog.getKindCombo().getSelectedName());
        }
      };

      myDialog.show();
      if (myDialog.getExitCode() == OK_EXIT_CODE) {
        SmartPsiElementPointer<T> pointer = created.get();
        return pointer == null ? null : pointer.getElement();
      }
      return null;
    }

    @Override
    public <T extends PsiElement> void show(@NotNull String errorTitle,
                                            @Nullable String selectedItem,
                                            @NotNull FileCreator<T> creator,
                                            Consumer<? super T> elementConsumer) {
      T element = show(errorTitle, selectedItem, creator);
      elementConsumer.consume(element);
    }

    @Override
    public @Nullable Map<String,String> getCustomProperties() {
      return null;
    }
  }

  private static final class NonBlockingPopupBuilderImpl implements Builder {
    private final @NotNull Project myProject;

    private @NlsContexts.PopupTitle String myTitle = LangBundle.message("popup.title.default.title");
    private String myDefaultText = null;
    private final List<TemplatePresentation> myTemplatesList = new ArrayList<>();
    private InputValidator myInputValidator;
    private final Map<String, InputValidator> myExtraValidators = new HashMap<>();
    private @Nullable Component dialogOwner;

    private NonBlockingPopupBuilderImpl(@NotNull Project project) {myProject = project;}

    @Override
    public Builder setTitle(@NlsContexts.PopupTitle String title) {
      myTitle = title;
      return this;
    }

    @Override
    public Builder setDefaultText(String text) {
      myDefaultText = text;
      return this;
    }

    @Override
    public Builder addKind(@Nls @NotNull String kind, @Nullable Icon icon, @NotNull String templateName,
                           @Nullable InputValidator extraValidator) {
      myTemplatesList.add(new TemplatePresentation(kind, icon, templateName));
      if (extraValidator != null) {
        myExtraValidators.put(templateName, extraValidator);
      }
      return this;
    }

    @Override
    public Builder setValidator(InputValidator validator) {
      myInputValidator = validator;
      return this;
    }

    @Override
    public Builder setDialogOwner(@Nullable Component owner) {
      dialogOwner = owner;
      return this;
    }

    @Override
    public @Nullable <T extends PsiElement> T show(@NotNull String errorTitle, @Nullable String selectedItem, @NotNull FileCreator<T> creator) {
      throw new UnsupportedOperationException("Modal dialog is not supported by this builder");
    }

    @Override
    public <T extends PsiElement> void show(@NotNull String errorTitle,
                                            @Nullable String selectedItem,
                                            @NotNull FileCreator<T> fileCreator,
                                            Consumer<? super T> elementConsumer) {
      CreateWithTemplatesDialogPanel contentPanel = new CreateWithTemplatesDialogPanel(selectedItem, myTemplatesList);
      ElementCreator elementCreator = new ElementCreator(myProject, errorTitle) {

        @Override
        protected PsiElement @NotNull [] create(@NotNull String newName) {
          T element = fileCreator.createFile(contentPanel.getEnteredName(), contentPanel.getSelectedTemplate());
          return element != null ? new PsiElement[]{element} : PsiElement.EMPTY_ARRAY;
        }

        @Override
        public boolean startInWriteAction() {
          return fileCreator.startInWriteAction();
        }

        @Override
        protected @NotNull String getActionName(@NotNull String newName) {
          return fileCreator.getActionName(newName, contentPanel.getSelectedTemplate());
        }
      };

      JBPopup popup = NewItemPopupUtil.createNewItemPopup(myTitle, contentPanel, contentPanel.getNameField());
      if (myDefaultText != null) {
        JTextField textField = contentPanel.getTextField();
        textField.setText(myDefaultText);
        textField.selectAll();
      }
      contentPanel.setApplyAction(e -> {
        String newElementName = contentPanel.getEnteredName();
        if (StringUtil.isEmptyOrSpaces(newElementName)) return;

        boolean isValid = myInputValidator == null || myInputValidator.canClose(newElementName);
        InputValidator extraValidator = myExtraValidators.get(contentPanel.getSelectedTemplate());
        boolean isExtraValid = extraValidator == null || extraValidator.canClose(newElementName);

        if (isValid && isExtraValid) {
          popup.closeOk(e);
          //noinspection unchecked
          T createdElement = (T)createElement(newElementName, elementCreator);
          if (createdElement != null) {
            elementConsumer.consume(createdElement);
          }
        }
        else {
          String errorMessage = Optional.ofNullable(!isValid ? myInputValidator : extraValidator)
            .filter(validator -> validator instanceof InputValidatorEx)
            .map(validator -> ((InputValidatorEx)validator).getErrorText(newElementName))
            .orElse(LangBundle.message("incorrect.name"));
          contentPanel.setError(errorMessage);
        }
      });

      Disposer.register(popup, contentPanel);
      if (dialogOwner == null)
        popup.showCenteredInCurrentWindow(myProject);
      else
        popup.showInCenterOf(dialogOwner);
    }

    @Override
    public @Nullable Map<String, String> getCustomProperties() {
      return null;
    }

    private static @Nullable PsiElement createElement(String newElementName, ElementCreator creator) {
      PsiElement[] elements = creator.tryCreate(newElementName);
      return elements.length > 0 ? elements[0] : null;
    }
  }

  public interface Builder {
    Builder setTitle(@DialogTitle String title);
    Builder setValidator(InputValidator validator);
    Builder setDefaultText(String text);
    Builder setDialogOwner(@Nullable Component owner);

    default Builder addKind(@NlsContexts.ListItem @NotNull String kind, @Nullable Icon icon, @NonNls @NotNull String templateName) {
      return addKind(kind, icon, templateName, null);
    }

    Builder addKind(@NlsContexts.ListItem @NotNull String kind,
                    @Nullable Icon icon,
                    @NonNls @NotNull String templateName,
                    @Nullable InputValidator extraValidator);

    @Nullable
    <T extends PsiElement> T show(@DialogTitle @NotNull String errorTitle,
                                  @NonNls @Nullable String selectedItem,
                                  @NotNull FileCreator<T> creator);

    <T extends PsiElement> void show(@DialogTitle @NotNull String errorTitle,
                                     @NonNls @Nullable String selectedItem,
                                     @NotNull FileCreator<T> creator,
                                     Consumer<? super T> elementConsumer);

    @Nullable
    Map<String,String> getCustomProperties();
  }

  public interface FileCreator<T> {

    @Nullable
    T createFile(@NonNls @NotNull String name, @NonNls @NotNull String templateName);

    @NlsContexts.Command
    @NotNull
    String getActionName(@NonNls @NotNull String name, @NonNls @NotNull String templateName);

    boolean startInWriteAction();
  }
}
