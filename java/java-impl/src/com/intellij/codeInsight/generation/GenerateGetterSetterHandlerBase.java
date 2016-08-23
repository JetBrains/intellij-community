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
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.exception.GenerateCodeException;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;
import org.jetbrains.java.generate.view.TemplatesPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class GenerateGetterSetterHandlerBase extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateGetterSetterHandlerBase");

  static {
    GenerateAccessorProviderRegistrar.registerProvider(s -> {
      if (s.getLanguage() != StdLanguages.JAVA) return Collections.emptyList();
      final List<EncapsulatableClassMember> result = new ArrayList<>();
      for (PsiField field : s.getFields()) {
        if (!(field instanceof PsiEnumConstant)) {
          result.add(new PsiFieldMember(field));
        }
      }
      return result;
    });
  }

  public GenerateGetterSetterHandlerBase(String chooserTitle) {
    super(chooserTitle);
  }

  @Override
  protected boolean hasMembers(@NotNull PsiClass aClass) {
    return !GenerateAccessorProviderRegistrar.getEncapsulatableClassMembers(aClass).isEmpty();
  }

  @Override
  protected String getHelpId() {
    return "Getter and Setter Templates Dialog";
  }

  @Override
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project, Editor editor) {
    final ClassMember[] allMembers = getAllOriginalMembers(aClass);
    if (allMembers == null) {
      HintManager.getInstance().showErrorHint(editor, getNothingFoundMessage());
      return null;
    }
    if (allMembers.length == 0) {
      HintManager.getInstance().showErrorHint(editor, getNothingAcceptedMessage());
      return null;
    }
    return chooseMembers(allMembers, false, false, project, editor);
  }

  protected static JComponent getHeaderPanel(final Project project, final TemplatesManager templatesManager, final String templatesTitle) {
    final JPanel panel = new JPanel(new BorderLayout());
    final JLabel templateChooserLabel = new JLabel(templatesTitle);
    panel.add(templateChooserLabel, BorderLayout.WEST);
    final ComboBox comboBox = new ComboBox();
    templateChooserLabel.setLabelFor(comboBox);
    comboBox.setRenderer(new ListCellRendererWrapper<TemplateResource>() {
      @Override
      public void customize(JList list, TemplateResource value, int index, boolean selected, boolean hasFocus) {
        setText(value.getName());
      }
    });
    final ComponentWithBrowseButton<ComboBox> comboBoxWithBrowseButton =
      new ComponentWithBrowseButton<>(comboBox, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final TemplatesPanel ui = new TemplatesPanel(project, templatesManager) {
            @Override
            protected boolean onMultipleFields() {
              return false;
            }

            @Nls
            @Override
            public String getDisplayName() {
              return StringUtil.capitalizeWords(UIUtil.removeMnemonic(StringUtil.trimEnd(templatesTitle, ":")), true);
            }
          };
          ui.setHint("Visibility is applied according to File | Settings | Editor | Code Style | Java | Code Generation");
          ui.selectNodeInTree(templatesManager.getDefaultTemplate());
          if (ShowSettingsUtil.getInstance().editConfigurable(panel, ui)) {
            setComboboxModel(templatesManager, comboBox);
          }
        }
      });

    setComboboxModel(templatesManager, comboBox);
    comboBox.addActionListener(new ActionListener() {
      public void actionPerformed(@NotNull final ActionEvent M) {
        templatesManager.setDefaultTemplate((TemplateResource)comboBox.getSelectedItem());
      }
    });

    panel.add(comboBoxWithBrowseButton, BorderLayout.CENTER);
    return panel;
  }

  private static void setComboboxModel(TemplatesManager templatesManager, ComboBox comboBox) {
    final Collection<TemplateResource> templates = templatesManager.getAllTemplates();
    comboBox.setModel(new DefaultComboBoxModel(templates.toArray(new TemplateResource[templates.size()])));
    comboBox.setSelectedItem(templatesManager.getDefaultTemplate());
  }

  @Override
  protected abstract String getNothingFoundMessage();
  protected abstract String getNothingAcceptedMessage();

  public boolean canBeAppliedTo(PsiClass targetClass) {
    final ClassMember[] allMembers = getAllOriginalMembers(targetClass);
    return allMembers != null && allMembers.length != 0;
  }

  @Override
  @Nullable
  protected ClassMember[] getAllOriginalMembers(final PsiClass aClass) {
    final List<EncapsulatableClassMember> list = GenerateAccessorProviderRegistrar.getEncapsulatableClassMembers(aClass);
    if (list.isEmpty()) {
      return null;
    }
    final List<EncapsulatableClassMember> members = ContainerUtil.findAll(list, member -> {
      try {
        return generateMemberPrototypes(aClass, member).length > 0;
      }
      catch (GenerateCodeException e) {
        return true;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return false;
      }
    });
    return members.toArray(new ClassMember[members.size()]);
  }


}
