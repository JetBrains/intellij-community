/*
 * Copyright 2001-2014 the original author or authors.
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
package org.jetbrains.java.generate;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.util.MemberChooser;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.TabbedConfigurable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.GenerateToStringClassFilter;
import org.jetbrains.java.generate.config.Config;
import org.jetbrains.java.generate.config.ConflictResolutionPolicy;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;
import org.jetbrains.java.generate.template.toString.ToStringTemplatesManager;
import org.jetbrains.java.generate.view.TemplatesPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;

/**
 * The action-handler that does the code generation.
 */
public final class GenerateToStringActionHandlerImpl implements GenerateToStringActionHandler, CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance(GenerateToStringActionHandlerImpl.class);

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiClass clazz = getSubjectClass(editor, psiFile);
    if (clazz == null) return;

    doExecuteAction(project, clazz, editor);
  }


  @Override
  public void executeActionQuickFix(final Project project, final PsiClass clazz) {
    doExecuteAction(project, clazz, null);
  }

  private static void doExecuteAction(final @NotNull Project project, final @NotNull PsiClass clazz, final Editor editor) {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(clazz)) {
      return;
    }
    LOG.debug("+++ doExecuteAction - START +++");

    if (LOG.isDebugEnabled()) {
      LOG.debug("Current project " + project.getName());
    }

    ReadAction.nonBlocking(() -> buildMembersToShow(clazz))
      .expireWhen(() -> !clazz.isValid() || project.isDisposed() || editor.isDisposed())
      .finishOnUiThread(ModalityState.defaultModalityState(), dialogMembers -> showChooser(project, clazz, editor, dialogMembers))
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private static void showChooser(@NotNull Project project,
                                @NotNull PsiClass clazz,
                                Editor editor,
                                PsiElementClassMember<?>[] dialogMembers) {
    final MemberChooserHeaderPanel header = new MemberChooserHeaderPanel(clazz);
    LOG.debug("Displaying member chooser dialog");

    final MemberChooser<PsiElementClassMember<?>> chooser =
      new MemberChooser<>(dialogMembers, true, true, project, PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, clazz), header) {
        @Override
        protected @NotNull String getHelpId() {
          return "editing.altInsert.tostring";
        }

        @Override
        protected boolean isInsertOverrideAnnotationSelected() {
          return JavaCodeStyleSettings.getInstance(clazz.getContainingFile()).INSERT_OVERRIDE_ANNOTATION;
        }
      };
    //noinspection DialogTitleCapitalization
    chooser.setTitle(JavaBundle.message("generate.tostring.title"));

    chooser.setCopyJavadocVisible(false);
    chooser.selectElements(getPreselection(clazz, dialogMembers));
    header.setChooser(chooser);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      chooser.close(DialogWrapper.OK_EXIT_CODE);
    }
    else {
      chooser.show();
    }
    if (DialogWrapper.OK_EXIT_CODE == chooser.getExitCode()) {
      Collection<PsiMember> selectedMembers = GenerationUtil.convertClassMembersToPsiMembers(sortedSelection(dialogMembers, chooser));

      final TemplateResource template = header.getSelectedTemplate();
      ToStringTemplatesManager.getInstance().setDefaultTemplate(template);

      if (template.isValidTemplate()) {
        final GenerateToStringWorker worker = new GenerateToStringWorker(clazz, editor, chooser.isInsertOverrideAnnotation());
        // decide what to do if the method already exists
        ConflictResolutionPolicy resolutionPolicy = worker.exitsMethodDialog(template);
        try {
          //noinspection DialogTitleCapitalization
          WriteCommandAction.runWriteCommandAction(project, JavaBundle.message("command.name.generate.tostring"), null,
                                                   () -> worker.execute(selectedMembers, template, resolutionPolicy));
        }
        catch (Exception e) {
          GenerationUtil.handleException(project, e);
        }
      }
      else {
        //noinspection DialogTitleCapitalization
        HintManager.getInstance().showErrorHint(editor,
                                                JavaBundle.message("hint.text.tostring.template.invalid", template.getFileName()));
      }
    }

    LOG.debug("+++ doExecuteAction - END +++");
  }

  private static @Nullable List<PsiElementClassMember<?>> sortedSelection(PsiElementClassMember<?>[] dialogMembers,
                                                                          MemberChooser<PsiElementClassMember<?>> chooser) {
    List<PsiElementClassMember<?>> selectedElements = chooser.getSelectedElements();
    if (selectedElements == null) {
      return null;
    }
    return GenerateToStringContext.getConfig().getSortElements() == 0
           ? selectedElements
           : ContainerUtil.filter(dialogMembers, selectedElements::contains);
  }

  private static PsiElementClassMember<?>[] getPreselection(@NotNull PsiClass clazz, PsiElementClassMember<?>[] dialogMembers) {
    return Arrays.stream(dialogMembers)
      .filter(member -> member.getElement().getContainingClass() == clazz)
      .toArray(PsiElementClassMember[]::new);
  }

  private static void updateDialog(PsiClass clazz, MemberChooser<? super PsiElementClassMember<?>> dialog) {
    final PsiElementClassMember<?>[] members = buildMembersToShow(clazz);
    dialog.resetElements(members);
    dialog.selectElements(getPreselection(clazz, members));
  }

  public static PsiElementClassMember<?>[] buildMembersToShow(PsiClass clazz) {
    Config config = GenerateToStringContext.getConfig();
    PsiField[] filteredFields = GenerateToStringUtils.filterAvailableFields(clazz, true, config.getFilterPattern());
    if (LOG.isDebugEnabled()) LOG.debug("Number of fields after filtering: " + filteredFields.length);
    PsiMethod[] filteredMethods;
    if (config.enableMethods) {
      // filter methods as it is enabled from config
      filteredMethods = GenerateToStringUtils.filterAvailableMethods(clazz, config.getFilterPattern());
      if (LOG.isDebugEnabled()) LOG.debug("Number of methods after filtering: " + filteredMethods.length);
    }
    else {
      filteredMethods = PsiMethod.EMPTY_ARRAY;
    }

    return GenerationUtil.combineToClassMemberList(filteredFields, filteredMethods);
  }

  private static @Nullable PsiClass getSubjectClass(Editor editor, final PsiFile file) {
    if (file == null) return null;

    int offset = editor.getCaretModel().getOffset();
    PsiElement context = file.findElementAt(offset);

    PsiClass clazz = PsiTreeUtil.getParentOfType(context, PsiClass.class, false);
    if (clazz == null) {
      if (file instanceof PsiJavaFile javaFile && javaFile.getClasses().length == 1 &&
          javaFile.getClasses()[0] instanceof PsiImplicitClass implicitClass &&
          implicitClass.getFirstChild() != null && PsiMethodUtil.hasMainMethod(implicitClass)) {
        clazz = implicitClass;
      }
      else {
        return null;
      }
    }

    //exclude interfaces, non-java classes etc
    for (GenerateToStringClassFilter filter : GenerateToStringClassFilter.EP_NAME.getExtensionList()) {
      if (!filter.canGenerateToString(clazz)) return null;
    }
    return clazz;
  }

  public static class MemberChooserHeaderPanel extends JPanel {
    private MemberChooser<PsiElementClassMember<?>> chooser;
    private final JComboBox<TemplateResource> comboBox;
    private Set<TemplateResource> inaccessibleTemplates = null;

    public void setChooser(MemberChooser<PsiElementClassMember<?>> chooser) {
      this.chooser = chooser;
    }

    public MemberChooserHeaderPanel(final PsiClass clazz) {
      super(new GridBagLayout());

      TemplatesManager templatesManager = ToStringTemplatesManager.getInstance();
      final Collection<TemplateResource> templates = templatesManager.getAllTemplates();

      final JButton settingsButton = new JButton(JavaBundle.message("button.text.settings"));
      settingsButton.setMnemonic(KeyEvent.VK_S);

      comboBox = new ComboBox<>(templates.toArray(new TemplateResource[0]));
      comboBox.addActionListener(e -> updateErrorMessage());
      Project project = clazz.getProject();
      final JavaPsiFacade instance = JavaPsiFacade.getInstance(project);
      final GlobalSearchScope resolveScope = clazz.getResolveScope();
      DumbService dumbService = DumbService.getInstance(project);
      ReadAction.nonBlocking(() -> {
          final Set<TemplateResource> invalid = new HashSet<>();
          for (TemplateResource template : templates) {
            String className = template.getClassName();
            if (className != null &&
                dumbService.computeWithAlternativeResolveEnabled(() -> instance.findClass(className, resolveScope)) == null) {
              invalid.add(template);
            }
          }
          return invalid;
        })
        .finishOnUiThread(ModalityState.any(), invalid -> {
          inaccessibleTemplates = invalid;
          updateErrorMessage();
          comboBox.repaint();
        })
        .submit(AppExecutorUtil.getAppExecutorService());
      comboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
        label.setText(value.getName());
        if (inaccessibleTemplates != null && inaccessibleTemplates.contains(value)) {
          label.setForeground(JBColor.RED);
        }
      }));
      settingsButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final TemplatesPanel ui = new TemplatesPanel(project);
          Configurable composite = new TabbedConfigurable() {
            @Override
            protected @NotNull List<Configurable> createConfigurables() {
              List<Configurable> res = new ArrayList<>();
              res.add(new GenerateToStringConfigurable(project));
              res.add(ui);
              return res;
            }

            @Override
            public String getDisplayName() {
              return JavaBundle.message("generate.tostring.tab.title");
            }

            @Override
            public String getHelpTopic() {
              return "editing.altInsert.tostring.settings";
            }

            @Override
            public void apply() throws ConfigurationException {
              super.apply();
              updateDialog(clazz, chooser);

              comboBox.removeAllItems();
              for (TemplateResource resource : templatesManager.getAllTemplates()) {
                comboBox.addItem(resource);
              }
              comboBox.setSelectedItem(templatesManager.getDefaultTemplate());
            }
          };

          ShowSettingsUtil.getInstance()
            .editConfigurable(MemberChooserHeaderPanel.this, composite, () -> ui.selectItem(templatesManager.getDefaultTemplate()));
          composite.disposeUIResources();
        }
      });

      comboBox.setSelectedItem(templatesManager.getDefaultTemplate());

      final JLabel templatesLabel = new JLabel(JavaBundle.message("generate.tostring.template.label"));
      templatesLabel.setLabelFor(comboBox);

      final GridBagConstraints constraints = new GridBagConstraints();
      constraints.anchor = GridBagConstraints.BASELINE;
      constraints.gridx = 0;
      add(templatesLabel, constraints);
      constraints.gridx = 1;
      constraints.weightx = 1.0;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      add(comboBox, constraints);
      constraints.gridx = 2;
      constraints.weightx = 0.0;
      add(settingsButton, constraints);
    }

    public TemplateResource getSelectedTemplate() {
      return (TemplateResource)comboBox.getSelectedItem();
    }

    private void updateErrorMessage() {
      if (chooser == null || chooser.isDisposed()) return;
      TemplateResource template = (TemplateResource)comboBox.getSelectedItem();
      if (template != null && inaccessibleTemplates != null && inaccessibleTemplates.contains(template)) {
        ComponentValidator validator = ComponentValidator.getInstance(comboBox)
          .orElseGet(() -> new ComponentValidator(chooser.getDisposable()).installOn(comboBox));
        String className = template.getClassName();
        String message = className != null
                         ? JavaBundle.message("dialog.message.class.not.found", className)
                         : JavaBundle.message("dialog.message.template.not.applicable");
        validator.updateInfo(new ValidationInfo(message, comboBox));
      }
      else {
        ComponentValidator.getInstance(comboBox).ifPresent(v -> v.updateInfo(null));
      }
    }
  }
}
