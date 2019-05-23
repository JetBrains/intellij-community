// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class CreatePackageDialog extends DialogWrapper {

  private static final int MIN_WIDTH = 500;
  private static final int MIN_HEIGHT = 30;

  private static final String DELIMITER = ".";
  private static final String REGEX_DELIMITER = Pattern.quote(DELIMITER);

  @NotNull private final Project myProject;
  @NotNull private final PsiDirectory myDirectory;
  @NotNull private final PsiDirectory myPackageRoot;
  @NotNull private final String myInitialText;
  private final List<JTextField> myPackageNamesFields = new ArrayList<>();

  private JComponent myFieldsPanel;
  private FixedSizeButton myAddButton;
  private boolean validationWarningsFound;

  private List<PsiDirectory> createdElements;

  CreatePackageDialog(@NotNull final Project project, @NotNull final PsiDirectory directory) {
    super(project, true);

    myProject = project;
    myDirectory = directory;
    myPackageRoot = getPackageRoot();
    myInitialText = buildInitialText();

    setTitle(IdeBundle.message("title.new.package"));

    init();
  }

  @NotNull
  public Optional<List<PsiDirectory>> showAndGetCreatedElements() {
    return Optional.ofNullable(showAndGet() ? createdElements : null);
  }

  @Override
  protected void doOKAction() {
    if (validationWarningsFound) {
      final String message = IdeBundle.message("warning.create.package.warnings.found");
      final String title = IdeBundle.message("warning.create.package.warnings.title");
      final int result = Messages.showYesNoDialog(myProject, message, title, Messages.getWarningIcon());
      if (result == Messages.NO) {
        return;
      }
    }

    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      LocalHistoryAction action = LocalHistory.getInstance().startAction(buildActionName());
      try {
        createdElements =
          ContainerUtil.map(myPackageNamesFields, field -> DirectoryUtil.createSubdirectories(field.getText(), myPackageRoot, DELIMITER));
      }
      catch (IncorrectOperationException ex) {
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showMessageDialog(myProject,
                                           CreateElementActionBase.filterMessage(ex.getMessage()),
                                           CommonBundle.getErrorTitle(),
                                           Messages.getErrorIcon())
        );
      }
      finally {
        action.finish();
      }
    }), IdeBundle.message("command.create.package"), null);

    super.doOKAction();
  }

  @NotNull
  private String buildActionName() {
    if (myPackageNamesFields.size() == 1) {
      String dirPath = myPackageRoot.getVirtualFile().getPresentableUrl();
      return IdeBundle.message("progress.creating.package", dirPath, myPackageNamesFields.get(0).getText());
    }

    return IdeBundle.message("progress.creating.packages");
  }

  @NotNull
  @Override
  protected List<ValidationInfo> doValidateAll() {
    validationWarningsFound = false;
    return myPackageNamesFields.stream().map(this::checkInput).filter(Objects::nonNull).collect(Collectors.toList());
  }

  @Nullable
  private ValidationInfo checkInput(@NotNull final JTextField field) {
    if (isDuplicateField(field)) {
      return new ValidationInfo(IdeBundle.message("error.duplicate.package.names"), field);
    }

    final String inputString = field.getText();

    if (inputString.endsWith(DELIMITER)) {
      return new ValidationInfo(IdeBundle.message("error.invalid.java.package.name.format"), field);
    }

    final PsiDirectoryFactory nameValidator = PsiDirectoryFactory.getInstance(myProject);
    VirtualFile file = myPackageRoot.getVirtualFile();

    ValidationInfo warning = null;

    for (String token : inputString.split(REGEX_DELIMITER)) {
      if (token.isEmpty()) {
        return new ValidationInfo(IdeBundle.message("error.invalid.java.package.name.format"), field);
      }

      if (file != null) {
        file = file.findChild(token);
        if (file != null && !file.isDirectory()) {
          return new ValidationInfo(IdeBundle.message("error.file.with.name.already.exists", token), field);
        }
      }

      if (!nameValidator.isValidPackageName(token)) {
        warning = new ValidationInfo(IdeBundle.message("warning.invalid.java.package.name"), field).asWarning().withOKEnabled();
      }

      if (FileTypeManager.getInstance().isFileIgnored(token)) {
        warning = new ValidationInfo(IdeBundle.message("warning.create.package.with.ignored.name"), field).asWarning().withOKEnabled();
      }
    }

    if (file != null) {
      return new ValidationInfo(IdeBundle.message("error.package.with.name.already.exists", file.getName()), field);
    }

    validationWarningsFound = validationWarningsFound || warning != null;
    return warning;
  }

  private boolean isDuplicateField(@NotNull final JTextField field) {
    return myPackageNamesFields.stream().anyMatch(f -> f != field && f.getText().equals(field.getText()));
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    final JLabel label = new JLabel(IdeBundle.message("prompt.enter.new.package.name"));
    label.setBorder(JBUI.Borders.empty(0, 10, 10, 0));
    return label;
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    myFieldsPanel = new JPanel(new GridLayout(0, 1));

    myAddButton = new FixedSizeButton();
    myAddButton.setIcon(PlatformIcons.ADD_ICON);

    addNewField();
    myPreferredFocusedComponent = myPackageNamesFields.get(0);
    myPreferredFocusedComponent.putClientProperty(DialogWrapperPeer.HAVE_INITIAL_SELECTION, true);

    myAddButton.addActionListener(e -> addNewField());
    myFieldsPanel.registerKeyboardAction(e -> addNewField(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                                         JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    final JBScrollPane contentPanel = new JBScrollPane(myFieldsPanel);
    contentPanel.setBorder(BorderFactory.createEmptyBorder());
    contentPanel.setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));

    return contentPanel;
  }

  private void addNewField() {
    if (!myPackageNamesFields.isEmpty()) {
      updateLastField(myPackageNamesFields.get(myPackageNamesFields.size() - 1));
    }

    final Container fieldContainer = Box.createHorizontalBox();

    final JTextField textField = new AutoResizableTextField(myInitialText);
    textField.select(myInitialText.length(), myInitialText.length());
    textField.setMaximumSize(new Dimension(Short.MAX_VALUE, textField.getPreferredSize().height));
    myPackageNamesFields.add(textField);
    fieldContainer.add(textField);

    myAddButton.setAttachedComponent(textField);
    fieldContainer.add(myAddButton);

    myFieldsPanel.add(fieldContainer);

    textField.requestFocus();
    packAndCenter();
  }

  private void updateLastField(@NotNull final JTextField lastField) {
    final Container lastContainer = lastField.getParent();
    final FixedSizeButton removeButton = new FixedSizeButton(lastField);
    removeButton.setIcon(PlatformIcons.DELETE_ICON);
    removeButton.addActionListener(e -> {
      myFieldsPanel.remove(lastContainer);
      myPackageNamesFields.remove(lastField);
      myFieldsPanel.revalidate();
      packAndCenter();
    });

    lastContainer.remove(myAddButton);
    lastContainer.add(removeButton, BorderLayout.EAST);
    lastContainer.revalidate();
  }

  private void packAndCenter() {
    pack();
    centerRelativeToParent();
  }

  @NotNull
  private String buildInitialText() {
    if (myPackageRoot.isEquivalentTo(myDirectory)) return "";

    final String root = myPackageRoot.getVirtualFile().getPath();
    final String current = myDirectory.getVirtualFile().getPath();

    return current.substring(root.length() + 1).replace("/", DELIMITER) + DELIMITER;
  }

  @NotNull
  private PsiDirectory getPackageRoot() {
    final PsiDirectoryFactory manager = PsiDirectoryFactory.getInstance(myProject);
    PsiDirectory directory = myDirectory;
    PsiDirectory parent = directory.getParent();

    while (parent != null && manager.isPackage(parent)) {
      directory = parent;
      parent = parent.getParent();
    }

    return directory;
  }

  private class AutoResizableTextField extends JBTextField {

    AutoResizableTextField(@NotNull final String initialText) {
      super(initialText);

      getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
          packAndCenter();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
          packAndCenter();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {

        }
      });
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.width += getColumnWidth() * 2;
      return size;
    }
  }
}
