// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
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
import java.util.Collection;
import java.util.List;

public abstract class GenerateGetterSetterHandlerBase extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance(GenerateGetterSetterHandlerBase.class);

  public GenerateGetterSetterHandlerBase(@NlsContexts.DialogTitle String chooserTitle) {
    super(chooserTitle);
  }

  @Override
  protected boolean hasMembers(@NotNull PsiClass aClass) {
    return !GenerateAccessorProviderRegistrar.getEncapsulatableClassMembers(aClass).isEmpty();
  }

  @Override
  protected String getHelpId() {
    return "Getter_and_Setter_Templates_Dialog";
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

  protected static JComponent getHeaderPanel(final Project project, final TemplatesManager templatesManager, final @Nls String templatesTitle) {
    final JPanel panel = new JPanel(new BorderLayout());
    final JLabel templateChooserLabel = new JLabel(templatesTitle);
    panel.add(templateChooserLabel, BorderLayout.WEST);
    final ComboBox<TemplateResource> comboBox = new ComboBox<>();
    templateChooserLabel.setLabelFor(comboBox);
    comboBox.setRenderer(SimpleListCellRenderer.create("", TemplateResource::getName));
    final ComponentWithBrowseButton<ComboBox<?>> comboBoxWithBrowseButton =
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
          ui.setHint(JavaBundle.message("generate.getter.setter.header.visibility.hint."));
          ui.selectNodeInTree(templatesManager.getDefaultTemplate());
          if (ShowSettingsUtil.getInstance().editConfigurable(panel, ui)) {
            setComboboxModel(templatesManager, comboBox);
          }
        }
      });

    setComboboxModel(templatesManager, comboBox);
    comboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull final ActionEvent M) {
        templatesManager.setDefaultTemplate((TemplateResource)comboBox.getSelectedItem());
      }
    });

    panel.add(comboBoxWithBrowseButton, BorderLayout.CENTER);
    return panel;
  }

  private static void setComboboxModel(TemplatesManager templatesManager, ComboBox<TemplateResource> comboBox) {
    final Collection<TemplateResource> templates = templatesManager.getAllTemplates();
    comboBox.setModel(new DefaultComboBoxModel<>(templates.toArray(new TemplateResource[0])));
    comboBox.setSelectedItem(templatesManager.getDefaultTemplate());
  }

  @Override
  protected abstract @NlsContexts.HintText String getNothingFoundMessage();
  protected abstract @NlsContexts.HintText String getNothingAcceptedMessage();

  public boolean canBeAppliedTo(PsiClass targetClass) {
    final ClassMember[] allMembers = getAllOriginalMembers(targetClass);
    return allMembers != null && allMembers.length != 0;
  }

  @Override
  protected ClassMember @Nullable [] getAllOriginalMembers(final PsiClass aClass) {
    final List<EncapsulatableClassMember> list = GenerateAccessorProviderRegistrar.getEncapsulatableClassMembers(aClass);
    if (list.isEmpty()) {
      return null;
    }
    final List<EncapsulatableClassMember> members = ContainerUtil.findAll(list, member -> {
      try {
        return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> generateMemberPrototypes(aClass, member).length > 0);
      }
      catch (GenerateCodeException e) {
        return true;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return false;
      }
    });
    return members.toArray(ClassMember.EMPTY_ARRAY);
  }
}
