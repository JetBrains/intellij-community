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

package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * @author peter
 */
public class CreateFileFromTemplateDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CreateFileFromTemplateDialog");
  private JTextField myNameField;
  private JComboBox myKindCombo;
  private JPanel myPanel;
  private JLabel myUpDownHint;

  private ElementCreator myCreator;

  private CreateFileFromTemplateDialog(@NotNull Project project, @NotNull final String title) {
    super(project, true);

    myKindCombo.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value == null) {
          LOG.error("Model: " + list.getModel().toString());
        }

        @SuppressWarnings({"unchecked"}) Trinity<String, Icon, String> _value = (Trinity<String, Icon, String>) value;
        setText(_value.first);
        setIcon(_value.second);
        return this;
      }
    });

    setTitle(title);
    //myNameLabel.setText(prompt);

    new ComboboxSpeedSearch(myKindCombo) {
      @Override
      protected String getElementText(Object element) {
        return ((Trinity<String, Icon, String>)element).first;
      }
    };

    final AnAction arrow = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (e.getInputEvent() instanceof KeyEvent) {
          final int code = ((KeyEvent)e.getInputEvent()).getKeyCode();
          final int delta = code == KeyEvent.VK_DOWN ? 1 : code == KeyEvent.VK_UP ? -1 : 0;

          final int size = myKindCombo.getModel().getSize();
          int next = myKindCombo.getSelectedIndex() + delta;
          if (next < 0 || next >= size) {
            if (!UISettings.getInstance().CYCLE_SCROLLING) {
              return;
            }
            next = (next + size) % size;
          }
          myKindCombo.setSelectedIndex(next);
        }
      }
    };
    final KeyboardShortcut up = new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), null);
    final KeyboardShortcut down = new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), null);
    arrow.registerCustomShortcutSet(new CustomShortcutSet(up, down), myNameField);

    myUpDownHint.setIcon(Icons.UP_DOWN_ARROWS);
    init();
  }

  private String getEnteredName() {
    return myNameField.getText();
  }

  private String getTemplateName() {
    //noinspection unchecked
    final Trinity<String, Icon, String> trinity = (Trinity<String, Icon, String>)myKindCombo.getSelectedItem();
    if (trinity == null) {
      LOG.error("Model: " + myKindCombo.getModel());
    }

    return trinity.third;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    if (myCreator.tryCreate(getEnteredName()).length == 0) {
      return;
    }
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  public static <T extends PsiElement> Builder createDialog(@NotNull final Project project, @NotNull final String title) {
    final CreateFileFromTemplateDialog dialog = new CreateFileFromTemplateDialog(project, title);

    return new Builder() {

      public Builder addKind(@NotNull String name, @Nullable Icon icon, @NotNull String templateName) {
        dialog.myKindCombo.addItem(new Trinity<String, Icon, String>(name, icon, templateName));
        return this;
      }

      public <T extends PsiElement> T show(@NotNull String errorTitle, @Nullable String selectedTemplateName,
                                           @NotNull final FileCreator<T> creator) {
        final Ref<T> created = Ref.create(null);
        if (selectedTemplateName != null) {
          Object item = null;
          ComboBoxModel model = dialog.myKindCombo.getModel();
          for (int i = 0, n = model.getSize(); i < n; i++) {
            Trinity<String, Icon, String> trinity = (Trinity<String, Icon, String>)model.getElementAt(i);
            if (selectedTemplateName.equals(trinity.third)) {
              item = trinity;
              break;
            }
          }
          if (item != null) {
            dialog.myKindCombo.setSelectedItem(item);
          }
        }
        dialog.myCreator = new ElementCreator(project, errorTitle) {
          @Override
          protected void checkBeforeCreate(String newName) throws IncorrectOperationException {
            creator.checkBeforeCreate(newName, dialog.getTemplateName());
          }

          @Override
          protected PsiElement[] create(String newName) throws Exception {
            final T element = creator.createFile(dialog.getEnteredName(), dialog.getTemplateName());
            created.set(element);
            if (element != null) {
              return new PsiElement[]{element};
            }
            return PsiElement.EMPTY_ARRAY;
          }

          @Override
          protected String getActionName(String newName) {
            return creator.getActionName(newName, dialog.getTemplateName());
          }
        };
        dialog.show();
        if (dialog.getExitCode() == OK_EXIT_CODE) {
          return created.get();
        }
        return null;
      }
    };
  }

  public interface Builder {
    Builder addKind(@NotNull String kind, @Nullable Icon icon, @NotNull String templateName);
    @Nullable
    <T extends PsiElement> T show(@NotNull String errorTitle, @Nullable String selectedItem, @NotNull FileCreator<T> creator);
  }

  public interface FileCreator<T> {
    void checkBeforeCreate(@NotNull String name, @NotNull String templateName) throws IncorrectOperationException;

    @Nullable
    T createFile(@NotNull String name, @NotNull String templateName);

    @NotNull
    String getActionName(@NotNull String name, @NotNull String templateName);
  }
}
