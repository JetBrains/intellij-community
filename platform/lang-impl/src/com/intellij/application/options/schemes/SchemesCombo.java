/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.MessageType;
import com.intellij.ui.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.Collection;
import java.util.function.Predicate;

public class SchemesCombo<T extends Scheme> {
  
  // region Message constants
  public static final String PROJECT_LEVEL = "Project";
  public static final String IDE_LEVEL = "IDE";
  public static final String EMPTY_NAME_MESSAGE = "The name must not be empty";
  public static final String NAME_ALREADY_EXISTS_MESSAGE = "Name is already in use. Please change to unique name.";
  private static final String EDITING_HINT = "Enter to save, Esc to cancel.";
  public static final int COMBO_WIDTH = 200;
  // endregion
  
  private ComboBox<MySchemeListItem<T>> myComboBox;
  private JPanel myRootPanel;
  private AbstractSchemesPanel<T> mySchemesPanel;
  private final CardLayout myLayout;
  private final JTextField myNameEditorField;
  private final MyComboBoxModel myComboBoxModel;
  
  private final static KeyStroke ESC_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
  private final static KeyStroke ENTER_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false);

  public SchemesCombo(@NotNull AbstractSchemesPanel<T> schemesPanel) {
    mySchemesPanel = schemesPanel;
    myLayout = new CardLayout();
    myRootPanel = new JPanel(myLayout);
    myComboBoxModel = new MyComboBoxModel();
    createCombo();
    myRootPanel.add(myComboBox);
    myNameEditorField = createNameEditorField();
    myRootPanel.add(myNameEditorField);
    myRootPanel.setPreferredSize(new Dimension(JBUI.scale(COMBO_WIDTH), myNameEditorField.getPreferredSize().height));
    myRootPanel.setMaximumSize(new Dimension(JBUI.scale(COMBO_WIDTH), Short.MAX_VALUE));
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
      protected void textChanged(DocumentEvent e) {
        validateOnTyping();
      }
    });
    return nameEditorField;
  }

  private void validateOnTyping() {
    String currName = myNameEditorField.getText();
    MySchemeListItem<T> selectedItem = getSelectedItem();
    if (selectedItem != null && !currName.equals(selectedItem.getSchemeName())) {
      String validationMessage = validateSchemeName(currName, mySchemesPanel.getModel().isProjectScheme(ObjectUtils.notNull(selectedItem.getScheme())));
      if (validationMessage != null) {
        mySchemesPanel.showInfo(validationMessage, MessageType.ERROR);
        return;
      }
    }
    showHint();
  }

  private void showHint() {
    mySchemesPanel.showInfo(EDITING_HINT, MessageType.INFO);
  }

  private void revertSchemeName() {
    MySchemeListItem<T> selectedItem = getSelectedItem();
    if (selectedItem != null) {
      myNameEditorField.setText(selectedItem.getSchemeName());
    }
  }

  public void updateSelected() {
    myComboBox.repaint();
  }

  private void stopEdit() {
    String newName = myNameEditorField.getText();
    MySchemeListItem<T> selectedItem = getSelectedItem();
    if (selectedItem != null) {
      if (newName.equals(selectedItem.getSchemeName())) {
        cancelEdit();
        return;
      }
      boolean isProjectScheme = mySchemesPanel.getModel().isProjectScheme(ObjectUtils.notNull(selectedItem.getScheme()));
      String validationMessage = validateSchemeName(newName, isProjectScheme);
      if (validationMessage != null) {
        mySchemesPanel.showInfo(validationMessage, MessageType.ERROR);
      }
      else {
        cancelEdit();
        if (selectedItem.getScheme() != null) {
          mySchemesPanel.getActions().renameScheme(selectedItem.getScheme(), newName);
        }
      }
    }
  }
  
  public void cancelEdit() {
    mySchemesPanel.clearInfo();
    myLayout.first(myRootPanel);
    myRootPanel.requestFocus();
  }

  private void createCombo() {
    myComboBox = new ComboBox<>(myComboBoxModel);
    myComboBox.setRenderer(new MyListCellRenderer());
    myComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySchemesPanel.getActions().onSchemeChanged(getSelectedScheme());
      }
    });
  }
  
  public void startEdit() {
    T scheme = getSelectedScheme();
    if (scheme != null) {
      showHint();
      myNameEditorField.setText(scheme.getName());
      myLayout.last(myRootPanel);
      myNameEditorField.requestFocus();
    }
  }

  private SimpleTextAttributes getSchemeAttributes(@NotNull MySchemeListItem<T> item) {
    SchemesModel<T> model = mySchemesPanel.getModel();
    T scheme = item.getScheme();
    SimpleTextAttributes baseAttributes = scheme !=null && model.canDeleteScheme(scheme)
           ? SimpleTextAttributes.REGULAR_ATTRIBUTES
           : SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
    if (scheme != null && model.canResetScheme(scheme) && model.differsFromDefault(scheme)) {
      return baseAttributes.derive(-1, JBColor.BLUE, null, null);
    }
    return baseAttributes;
  }

  public void resetSchemes(@NotNull Collection<T> schemes) {
    myComboBoxModel.removeAllElements();
    SchemesModel<T> model = mySchemesPanel.getModel();
    if (mySchemesPanel.getModel().supportsProjectSchemes()) {
      myComboBoxModel.addElement(new MySeparatorItem(PROJECT_LEVEL));
      addItems(schemes, scheme -> model.isProjectScheme(scheme));
      myComboBoxModel.addElement(new MySeparatorItem(IDE_LEVEL));
      addItems(schemes, scheme -> !model.isProjectScheme(scheme));
    }
    else {
      addItems(schemes, scheme -> true);
    }
  }
  
  private void addItems(@NotNull Collection<T> schemes, Predicate<T> filter) {
    for (T scheme : schemes) {
      if (filter.test(scheme)) {
        myComboBoxModel.addElement(new MySchemeListItem<>(scheme));
      }
    }
  }

  private class MyListCellRenderer extends ColoredListCellRenderer<MySchemeListItem<T>> {
    private ListCellRendererWrapper<MySchemeListItem> myWrapper = new ListCellRendererWrapper<MySchemeListItem>() {
      @Override
      public void customize(JList list,
                            MySchemeListItem value,
                            int index,
                            boolean selected,
                            boolean hasFocus) {
        if (value.isSeparator()) {
          setText(" Stored in " + value.getPresentableText());
          setSeparator();
        }
      }
    };

    @Override
    public Component getListCellRendererComponent(JList<? extends MySchemeListItem<T>> list,
                                                  MySchemeListItem<T> value,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      if (value.isSeparator()) {
        Component c = myWrapper.getListCellRendererComponent(list, value, index, selected, hasFocus);
        if (c instanceof TitledSeparator) {
          ((TitledSeparator)c).getLabel().setForeground(JBColor.GRAY);
          return c;
        }
      }
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends MySchemeListItem<T>> list,
                                         MySchemeListItem<T> value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      T scheme = value.getScheme();
      if (scheme != null) {
        append(value.getPresentableText(), getSchemeAttributes(value));
        if (mySchemesPanel.getModel().supportsProjectSchemes()) {
          if (index == -1) {
            append("  " + (mySchemesPanel.getModel().isProjectScheme(scheme) ? PROJECT_LEVEL : IDE_LEVEL),
                   SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }
      }
    }
  }

  @Nullable
  public T getSelectedScheme() {
    MySchemeListItem<T> item = getSelectedItem();
    return item != null ? item.getScheme() : null;
  }
  
  @Nullable
  public MySchemeListItem<T> getSelectedItem() {
    int i = myComboBox.getSelectedIndex();
    return i >= 0 ? myComboBox.getItemAt(i) : null;
  }
  
  public void selectScheme(@Nullable T scheme) {
    for (int i = 0; i < myComboBox.getItemCount(); i ++) {
      if (myComboBox.getItemAt(i).getScheme() == scheme) {
        myComboBox.setSelectedIndex(i);
        break;
      }
    }
  }
  
  public JComponent getComponent() {
    return myRootPanel;
  }
  
  private class MySeparatorItem extends MySchemeListItem<T> {
    
    private String myTitle;

    public MySeparatorItem(@NotNull String title) {
      super(null);
      myTitle = title;
    }

    @Override
    public boolean isSeparator() {
      return true;
    }

    @NotNull
    @Override
    public String getPresentableText() {
      return myTitle;
    }
  }

  private static class MySchemeListItem<T extends Scheme> {

    private @Nullable T myScheme;

    public MySchemeListItem(@Nullable T scheme) {
      myScheme = scheme;
    }

    @Nullable
    public String getSchemeName() {
      return myScheme != null ? myScheme.getName() : null;
    }

    @Nullable
    public T getScheme() {
      return myScheme;
    }

    @NotNull
    public String getPresentableText() {
      return myScheme != null ? SchemeManager.getDisplayName(myScheme) : "";
    }

    public boolean isSeparator() {
      return false;
    }
    
  }

  @Nullable
  private String validateSchemeName(@NotNull String name, boolean isProjectScheme) {
    if (name.isEmpty()) {
      return EMPTY_NAME_MESSAGE;
    }
    else if (mySchemesPanel.getModel().containsScheme(name, isProjectScheme)) {
      return NAME_ALREADY_EXISTS_MESSAGE;
    }
    return null;
  }
  
  private class MyComboBoxModel extends DefaultComboBoxModel<MySchemeListItem<T>> {

    @Override
    public void setSelectedItem(Object anObject) {
      if (anObject instanceof MySchemeListItem && ((MySchemeListItem)anObject).isSeparator()) {
        return;
      }
      super.setSelectedItem(anObject);
    }
  }
}
