/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.ide.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.ui.ComboboxSpeedSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public class CreateFileFromTemplateDialog extends DialogWrapper {
  private JTextField myNameField;
  private JComboBox myKindCombo;
  private JPanel myPanel;

  private ElementCreator myCreator;

  private CreateFileFromTemplateDialog(@NotNull Project project, @NotNull final String title) {
    super(project, true);

    myKindCombo.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
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
        return ((Trinity<String, Icon, String>)element).third;
      }
    };

    init();
  }

  private String getEnteredName() {
    return myNameField.getText();
  }

  private String getTemplateName() {
    //noinspection unchecked
    return ((Trinity<String, Icon, String>)myKindCombo.getSelectedItem()).third;
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

  public static Builder createDialog(@NotNull final Project project, @NotNull final String title) {
    final CreateFileFromTemplateDialog dialog = new CreateFileFromTemplateDialog(project, title);

    return new Builder() {

      public Builder addKind(@NotNull String name, @Nullable Icon icon, @NotNull String templateName) {
        dialog.myKindCombo.addItem(new Trinity<String, Icon, String>(name, icon, templateName));
        return this;
      }

      public PsiElement show(@NotNull String errorTitle, @NotNull final FileCreator creator) {
        final Ref<PsiElement> created = Ref.create(null);
        dialog.myCreator = new ElementCreator(project, errorTitle) {
          @Override
          protected void checkBeforeCreate(String newName) throws IncorrectOperationException {
            creator.checkBeforeCreate(newName, dialog.getTemplateName());
          }

          @Override
          protected PsiElement[] create(String newName) throws Exception {
            final PsiElement element = creator.createFile(dialog.getEnteredName(), dialog.getTemplateName());
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
    PsiElement show(@NotNull String errorTitle, @NotNull FileCreator creator);
  }

  public interface FileCreator {
    void checkBeforeCreate(@NotNull String name, @NotNull String templateName) throws IncorrectOperationException;

    @Nullable
    PsiElement createFile(@NotNull String name, @NotNull String templateName);

    @NotNull
    String getActionName(@NotNull String name, @NotNull String templateName);
  }
}
