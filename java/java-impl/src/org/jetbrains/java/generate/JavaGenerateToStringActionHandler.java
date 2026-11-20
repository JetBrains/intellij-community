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
import com.intellij.codeInsight.options.JavaConfigurationDialogInfo;
import com.intellij.codeInsight.options.JavaConfigurationDialogKind;
import com.intellij.ide.util.MemberChooser;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
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

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The action-handler that does the code generation.
 */
public class JavaGenerateToStringActionHandler implements GenerateToStringActionHandler, CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance(JavaGenerateToStringActionHandler.class);
  private final TemplatesManager myTemplatesManager;

  public JavaGenerateToStringActionHandler() {
    this(ToStringTemplatesManager.getInstance());
  }

  protected JavaGenerateToStringActionHandler(@NotNull TemplatesManager templatesManager) {
    myTemplatesManager = templatesManager;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    ActionContext context = ActionContext.from(editor, psiFile);
    PsiClass clazz = GenerateToStringClassFilter.findTargetClass(context);
    if (clazz == null) return;

    doExecuteAction(project, clazz, editor);
  }


  @Override
  public void executeActionQuickFix(final Project project, final PsiClass clazz) {
    doExecuteAction(project, clazz, null);
  }

  private void doExecuteAction(final @NotNull Project project, final @NotNull PsiClass clazz, final Editor editor) {
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

  private void showChooser(@NotNull Project project,
                           @NotNull PsiClass clazz,
                           Editor editor,
                           PsiElementClassMember<?>[] dialogMembers) {
    final MemberChooserHeaderPanel header = new MemberChooserHeaderPanel(clazz, myTemplatesManager);
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
    //noinspection SplitModeApiUsage
    if (DialogWrapper.OK_EXIT_CODE == chooser.getExitCode()) {
      Collection<PsiMember> selectedMembers = GenerationUtil.convertClassMembersToPsiMembers(sortedSelection(dialogMembers, chooser));

      final TemplateResource template = header.getSelectedTemplate();
      myTemplatesManager.setDefaultTemplate(template);

      if (template.isValidTemplate()) {
        final GenerateToStringWorker worker = new GenerateToStringWorker(clazz, editor, chooser.isInsertOverrideAnnotation());
        //noinspection DialogTitleCapitalization
        String title = JavaBundle.message("command.name.generate.tostring");
        ReadAction.nonBlocking(() -> worker.findExistingMethodName(template))
          .expireWhen(() -> !clazz.isValid() || project.isDisposed())
          .finishOnUiThread(ModalityState.defaultModalityState(), existingMethodName -> {
            // decide what to do if the method already exists
            ConflictResolutionPolicy resolutionPolicy = worker.chooseConflictPolicy(existingMethodName);
            try {
              CommandProcessor.getInstance().executeCommand(project, () -> {
                ((ApplicationEx)ApplicationManager.getApplication()).runWriteActionWithNonCancellableProgressInDispatchThread(
                  title, project, null, indicator -> worker.execute(selectedMembers, template, resolutionPolicy));
              }, title, null);
            }
            catch (Exception e) {
              GenerationUtil.handleException(project, e);
            }
          })
          .submit(AppExecutorUtil.getAppExecutorService());
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

  public static class MemberChooserHeaderPanel extends JPanel {
    private MemberChooser<PsiElementClassMember<?>> chooser;
    private final JComboBox<TemplateResource> comboBox;
    private Set<TemplateResource> inaccessibleTemplates = null;

    public void setChooser(MemberChooser<PsiElementClassMember<?>> chooser) {
      this.chooser = chooser;
    }

    public MemberChooserHeaderPanel(final PsiClass clazz, @NotNull TemplatesManager templatesManager) {
      super(new GridBagLayout());

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
      JavaConfigurationDialogInfo settingsInfo =
        new JavaConfigurationDialogInfo(JavaConfigurationDialogKind.TO_STRING_TEMPLATE_SETTINGS, clazz.getLanguage());
      settingsButton.setVisible(settingsInfo.isConfigurationUISupported());
      settingsButton.addActionListener(_ -> {
        Config configBefore = GenerateToStringContext.getConfig();
        List<TemplateResource> templatesBefore = List.copyOf(templatesManager.getAllTemplates());
        TemplateResource defaultTemplateBefore = templatesManager.getDefaultTemplate();
        settingsInfo.showConfigurationDialog(this, project);
        if (!configBefore.equals(GenerateToStringContext.getConfig())) {
          updateDialog(clazz, chooser);
        }
        Collection<TemplateResource> templatesAfter = templatesManager.getAllTemplates();
        TemplateResource defaultTemplateAfter = templatesManager.getDefaultTemplate();
        if (!templatesBefore.equals(List.copyOf(templatesAfter)) || !defaultTemplateBefore.equals(defaultTemplateAfter)) {
          comboBox.removeAllItems();
          for (TemplateResource resource : templatesAfter) {
            comboBox.addItem(resource);
          }
          comboBox.setSelectedItem(defaultTemplateAfter);
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
