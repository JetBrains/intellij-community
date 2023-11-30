// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.schemes;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.Collection;
import java.util.function.Consumer;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

public final class EditableSchemesCombo<T extends Scheme> {

  public static final int COMBO_WIDTH = 200;
  // endregion

  private SchemesCombo<T> myComboBox;
  private final JPanel myRootPanel;
  private final AbstractSchemesPanel<T, ?> mySchemesPanel;
  private final CardLayout myLayout;
  private final JTextField myNameEditorField;
  private @Nullable NameEditData myNameEditData;

  private static final KeyStroke ESC_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
  private static final KeyStroke ENTER_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false);
  private static final Color MODIFIED_ITEM_FOREGROUND = JBColor.namedColor("ComboBox.modifiedItemForeground", JBColor.BLUE);

  public EditableSchemesCombo(@NotNull AbstractSchemesPanel<T, ?> schemesPanel) {
    mySchemesPanel = schemesPanel;
    myLayout = new CardLayout();
    myRootPanel = new JPanel(myLayout);
    createCombo();
    myRootPanel.add(myComboBox);
    myNameEditorField = createNameEditorField();
    myRootPanel.add(myNameEditorField);
    myRootPanel.setPreferredSize(new Dimension(JBUIScale.scale(COMBO_WIDTH), myNameEditorField.getPreferredSize().height));
    myRootPanel.setMaximumSize(new Dimension(JBUIScale.scale(COMBO_WIDTH), Short.MAX_VALUE));
  }

  private JTextField createNameEditorField() {
    JTextField nameEditorField = new JTextField();
    nameEditorField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        revertSchemeName();
        cancelEdit();
      }
    }, ESC_KEY_STROKE, JComponent.WHEN_FOCUSED);
    nameEditorField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        stopEdit();
      }
    }, ENTER_KEY_STROKE, JComponent.WHEN_FOCUSED);
    nameEditorField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        stopEdit();
      }
    });
    nameEditorField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        validateOnTyping();
      }
    });
    return nameEditorField;
  }

  private void validateOnTyping() {
    if (myNameEditData == null) return;
    String currName = myNameEditorField.getText();
    if (!currName.equals(myNameEditData.initialName)) {
      String validationMessage = validateSchemeName(currName, myNameEditData.isProjectScheme);
      if (validationMessage != null) {
        mySchemesPanel.showInfo(validationMessage, MessageType.ERROR);
        return;
      }
    }
    showHint();
  }

  private void showHint() {
    mySchemesPanel.showInfo(IdeBundle.message("hint.scheme.editing"), MessageType.INFO);
  }

  private void revertSchemeName() {
    if (myNameEditData != null) {
      myNameEditorField.setText(myNameEditData.initialName);
    }
  }

  public void updateSelected() {
    myComboBox.repaint();
  }

  private void stopEdit() {
    if (myNameEditData == null) {
      cancelEdit();
      return;
    }
    String newName = myNameEditorField.getText();
    String validationMessage = validateSchemeName(newName, myNameEditData.isProjectScheme);
    if (validationMessage != null) {
      mySchemesPanel.showInfo(validationMessage, MessageType.ERROR);
    }
    else {
      myNameEditData.nameConsumer.accept(newName);
      cancelEdit();
    }
  }

  public void cancelEdit() {
    mySchemesPanel.clearInfo();
    myLayout.first(myRootPanel);
    myNameEditData = null;
    final IdeFocusManager focusManager = IdeFocusManager.getGlobalInstance();
    focusManager.doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myRootPanel, true));
  }

  private void createCombo() {
    myComboBox = new SchemesCombo<>() {
      @Override
      protected boolean supportsProjectSchemes() {
        return mySchemesPanel.supportsProjectSchemes();
      }

      @Override
      protected boolean isProjectScheme(@NotNull T scheme) {
        return mySchemesPanel.getModel().isProjectScheme(scheme);
      }

      @Override
      protected boolean isDefaultScheme(@NotNull T scheme) {
        SchemesModel<T> model = mySchemesPanel.getModel();
        return model.isDefaultScheme(scheme);
      }

      @Override
      protected int getIndent(@NotNull T scheme) {
        return mySchemesPanel.getIndent(scheme);
      }

      @Override
      protected @NotNull SimpleTextAttributes getSchemeAttributes(T scheme) {
        SchemesModel<T> model = mySchemesPanel.getModel();
        SimpleTextAttributes baseAttributes = !useBoldForNonRemovableSchemes() || model.canDeleteScheme(scheme)
                                              ? SimpleTextAttributes.REGULAR_ATTRIBUTES
                                              : SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
        if (mySchemesPanel.highlightNonDefaultSchemes() && model.canResetScheme(scheme) && model.differsFromDefault(scheme)) {
          return baseAttributes.derive(-1, MODIFIED_ITEM_FOREGROUND, null, null);
        }
        return baseAttributes;
      }
    };
    myComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySchemesPanel.getActions().onSchemeChanged(getSelectedScheme());
      }
    });
  }

  public void startEdit(@NotNull String initialName, boolean isProjectScheme, @NotNull Consumer<? super String> nameConsumer) {
    showHint();
    myNameEditData = new NameEditData(initialName, nameConsumer, isProjectScheme);
    myNameEditorField.setText(initialName);
    myLayout.last(myRootPanel);
    SwingUtilities.invokeLater(() -> {
      final IdeFocusManager focusManager = IdeFocusManager.getGlobalInstance();
      focusManager.doWhenFocusSettlesDown(() -> focusManager.requestFocus(myNameEditorField, true));
    });
  }

  public void resetSchemes(@NotNull Collection<? extends T> schemes) {
    myComboBox.resetSchemes(schemes);
  }

  public @Nullable T getSelectedScheme() {
    return myComboBox.getSelectedScheme();
  }

  public void selectScheme(@Nullable T scheme) {
    myComboBox.selectScheme(scheme);
  }

  public JComponent getComponent() {
    return myRootPanel;
  }

  private boolean useBoldForNonRemovableSchemes() {
    return mySchemesPanel.useBoldForNonRemovableSchemes();
  }

  private @Nullable @Nls String validateSchemeName(@NotNull String name, boolean isProjectScheme) {
    if (myNameEditData != null && name.equals(myNameEditData.initialName)) return null;
    if (isEmptyOrSpaces(name)) {
      return IdeBundle.message("error.empty.name");
    }
    else if (mySchemesPanel.getModel().containsScheme(name, isProjectScheme)) {
      return IdeBundle.message("error.name.already.exists");
    }
    return null;
  }

  private static final class NameEditData {
    private final @NotNull String initialName;
    private final @NotNull Consumer<? super String> nameConsumer;
    private final boolean isProjectScheme;

    private NameEditData(@NotNull String name, @NotNull Consumer<? super String> nameConsumer, boolean isProjectScheme) {
      initialName = name;
      this.nameConsumer = nameConsumer;
      this.isProjectScheme = isProjectScheme;
    }
  }
}
