// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.ui;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManager;
import com.intellij.codeInsight.generation.GenerateEqualsHelper;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.classMembers.AbstractMemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.classMembers.MemberInfoTooltipManager;
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.psi.PsiAdapter;
import org.jetbrains.java.generate.template.TemplateResource;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

public class GenerateEqualsWizard extends AbstractGenerateEqualsWizard<PsiClass, PsiMember, MemberInfo> {
  private static final Logger LOG = Logger.getInstance(GenerateEqualsWizard.class);

  private static final MyMemberInfoFilter MEMBER_INFO_FILTER = new MyMemberInfoFilter();

  public static final class JavaGenerateEqualsWizardBuilder extends AbstractGenerateEqualsWizard.Builder<PsiClass, PsiMember, MemberInfo> {
    private final @NotNull PsiClass myClass;

    private final MemberSelectionPanel myEqualsPanel;
    private final MemberSelectionPanel myHashCodePanel;
    private final MemberSelectionPanel myNonNullPanel;
    private final HashMap<PsiMember, MemberInfo> myFieldsToHashCode;
    private final HashMap<PsiMember, MemberInfo> myFieldsToNonNull;
    private final List<MemberInfo> myClassFields;

    private JavaGenerateEqualsWizardBuilder(@NotNull PsiClass aClass, boolean needEquals, boolean needHashCode) {
      LOG.assertTrue(needEquals || needHashCode);
      myClass = aClass;
      myClassFields = MemberInfo.extractClassMembers(myClass, MEMBER_INFO_FILTER, false);
      for (MemberInfo myClassField : myClassFields) {
        myClassField.setChecked(true);
      }
      if (needEquals) {
        String title = JavaBundle.message("generate.equals.hashcode.equals.fields.chooser.title");
        myEqualsPanel = new MemberSelectionPanel(title, myClassFields, null);
        myEqualsPanel.getTable().setMemberInfoModel(new EqualsMemberInfoModel());
      }
      else {
        myEqualsPanel = null;
      }
      if (needHashCode) {
        final List<MemberInfo> hashCodeMemberInfos;
        if (needEquals) {
          myFieldsToHashCode = createFieldToMemberInfoMap(true);
          hashCodeMemberInfos = Collections.emptyList();
        }
        else {
          hashCodeMemberInfos = myClassFields;
          myFieldsToHashCode = null;
        }
        String title = JavaBundle.message("generate.equals.hashcode.hashcode.fields.chooser.title");
        myHashCodePanel = new MemberSelectionPanel(title, hashCodeMemberInfos, null);
        myHashCodePanel.getTable().setMemberInfoModel(new HashCodeMemberInfoModel());
        if (needEquals) {
          updateHashCodeMemberInfos(myClassFields);
        }
      }
      else {
        myHashCodePanel = null;
        myFieldsToHashCode = null;
      }
      String title = JavaBundle.message("generate.equals.hashcode.non.null.fields.chooser.title");
      myNonNullPanel = new MemberSelectionPanel(title, Collections.emptyList(), null);
      myFieldsToNonNull = createFieldToMemberInfoMap(false);

      ReadAction.nonBlocking(() -> {
          Map<PsiMember, Boolean> result = new HashMap<>();
          return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
            for (PsiMember member : myFieldsToNonNull.keySet()) {
              boolean checked = NullableNotNullManager.isNotNull(member) ||
                                member instanceof PsiField field &&
                                NullabilityUtil.getNullabilityFromFieldInitializers(field).second == Nullability.NOT_NULL;
              result.put(member, checked);
            }
            return result;
          });
        })
        .finishOnUiThread(ModalityState.any(), m -> {
          for (Map.Entry<PsiMember, Boolean> entry : m.entrySet()) {
            myFieldsToNonNull.get(entry.getKey()).setChecked(entry.getValue());
          }
        })
        .submit(AppExecutorUtil.getAppExecutorService());
    }

    @Override
    protected List<MemberInfo> getClassFields() {
      return myClassFields;
    }

    @Override
    protected HashMap<PsiMember, MemberInfo> getFieldsToHashCode() {
      return myFieldsToHashCode;
    }

    @Override
    protected HashMap<PsiMember, MemberInfo> getFieldsToNonNull() {
      return myFieldsToNonNull;
    }

    @Override
    protected AbstractMemberSelectionPanel<PsiMember, MemberInfo> getEqualsPanel() {
      return myEqualsPanel;
    }

    @Override
    protected AbstractMemberSelectionPanel<PsiMember, MemberInfo> getHashCodePanel() {
      return myHashCodePanel;
    }

    @Override
    protected AbstractMemberSelectionPanel<PsiMember, MemberInfo> getNonNullPanel() {
      return myNonNullPanel;
    }

    @Override
    protected PsiClass getPsiClass() {
      return myClass;
    }

    @Override
    protected void updateHashCodeMemberInfos(Collection<? extends MemberInfo> equalsMemberInfos) {
      if (myHashCodePanel == null) return;
      List<MemberInfo> hashCodeFields = new ArrayList<>();

      for (MemberInfo equalsMemberInfo : equalsMemberInfos) {
        hashCodeFields.add(myFieldsToHashCode.get(equalsMemberInfo.getMember()));
      }

      myHashCodePanel.getTable().setMemberInfos(hashCodeFields);
    }

    @Override
    protected void updateNonNullMemberInfos(Collection<? extends MemberInfo> equalsMemberInfos) {
      final ArrayList<MemberInfo> list = new ArrayList<>();

      for (MemberInfo equalsMemberInfo : equalsMemberInfos) {
        if (mayNeedNullCheck((PsiField)equalsMemberInfo.getMember())) {
          list.add(myFieldsToNonNull.get(equalsMemberInfo.getMember()));
        }
      }
      myNonNullPanel.getTable().setMemberInfos(list);
    }

    private HashMap<PsiMember, MemberInfo> createFieldToMemberInfoMap(boolean checkedByDefault) {
      Collection<MemberInfo> memberInfos = MemberInfo.extractClassMembers(myClass, MEMBER_INFO_FILTER, false);
      final HashMap<PsiMember, MemberInfo> result = new HashMap<>();
      for (MemberInfo memberInfo : memberInfos) {
        memberInfo.setChecked(checkedByDefault);
        result.put(memberInfo.getMember(), memberInfo);
      }
      return result;
    }

  }

  public GenerateEqualsWizard(Project project, @NotNull PsiClass aClass, boolean needEquals, boolean needHashCode) {
    super(project, new JavaGenerateEqualsWizardBuilder(aClass, needEquals, needHashCode));
  }

  public PsiField[] getEqualsFields() {
    if (myEqualsPanel != null) {
      return memberInfosToFields(myEqualsPanel.getTable().getSelectedMemberInfos());
    }
    else {
      return null;
    }
  }

  public PsiField[] getHashCodeFields() {
    if (myHashCodePanel != null) {
      return memberInfosToFields(myHashCodePanel.getTable().getSelectedMemberInfos());
    }
    else {
      return null;
    }
  }

  public PsiField[] getNonNullFields() {
    return memberInfosToFields(myNonNullPanel.getTable().getSelectedMemberInfos());
  }

  private static PsiField[] memberInfosToFields(Collection<? extends MemberInfo> infos) {
    ArrayList<PsiField> list = new ArrayList<>();
    for (MemberInfo info : infos) {
      list.add((PsiField)info.getMember());
    }
    return list.toArray(PsiField.EMPTY_ARRAY);
  }

  @Override
  protected int getNextStep(int step) {
    if (step + 1 == getNonNullStepCode()) {
      if (templateDependsOnFieldsNullability()) {
        for (MemberInfo classField : myClassFields) {
          if (classField.isChecked() && mayNeedNullCheck((PsiField)classField.getMember())) {
            return getNonNullStepCode();
          }
        }
      }
      return step;
    }

    return super.getNextStep(step);
  }

  private static boolean mayNeedNullCheck(PsiField field) {
    PsiType type = field.getType();
    return !(type instanceof PsiPrimitiveType) && !(type instanceof PsiArrayType);
  }

  private static boolean templateDependsOnFieldsNullability() {
    final EqualsHashCodeTemplatesManager templatesManager = EqualsHashCodeTemplatesManager.getInstance();
    final String notNullCheckPresent = "\\.notNull\\W";
    final Pattern pattern = Pattern.compile(notNullCheckPresent);
    return pattern.matcher(templatesManager.getDefaultEqualsTemplate().getTemplate()).find() ||
           pattern.matcher(templatesManager.getDefaultHashcodeTemplate().getTemplate()).find();
  }

  @Override
  protected void addSteps() {
    if (myEqualsPanel != null) {
      addStep(new TemplateChooserStep(myClass));
    }
    super.addSteps();
  }

  private static class MyMemberInfoFilter implements MemberInfoBase.Filter<PsiMember> {
    @Override
    public boolean includeMember(PsiMember element) {
      return element instanceof PsiField && !element.hasModifierProperty(PsiModifier.STATIC);
    }
  }

  private static class EqualsMemberInfoModel extends AbstractMemberInfoModel<PsiMember, MemberInfo> {
    MemberInfoTooltipManager<PsiMember, MemberInfo> myTooltipManager =
      new MemberInfoTooltipManager<>(new MemberInfoTooltipManager.TooltipProvider<>() {
        @Override
        public String getTooltip(MemberInfo memberInfo) {
          if (checkForProblems(memberInfo) == OK) return null;
          if (!(memberInfo.getMember() instanceof PsiField field)) return JavaBundle.message("generate.equals.hashcode.internal.error");
          if (!JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_5)) {
            final PsiType type = field.getType();
            if (PsiAdapter.isNestedArray(type)) {
              return JavaBundle.message("generate.equals.warning.equals.for.nested.arrays.not.supported");
            }
            if (GenerateEqualsHelper.isArrayOfObjects(type)) {
              return JavaBundle.message("generate.equals.warning.generated.equals.could.be.incorrect");
            }
          }
          return null;
        }
      });

    @Override
    public boolean isMemberEnabled(MemberInfo member) {
      return member.getMember() instanceof PsiField;
    }

    @Override
    public int checkForProblems(@NotNull MemberInfo member) {
      if (!(member.getMember() instanceof PsiField field)) return ERROR;
      final PsiType type = field.getType();
      if (!JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_5)) {
        if (PsiAdapter.isNestedArray(type)) return ERROR;
        if (GenerateEqualsHelper.isArrayOfObjects(type)) return WARNING;
      }
      return OK;
    }

    @NlsContexts.Tooltip
    @Override
    public String getTooltipText(MemberInfo member) {
      return myTooltipManager.getTooltip(member);
    }
  }

  private static class HashCodeMemberInfoModel extends AbstractMemberInfoModel<PsiMember, MemberInfo> {
    private final MemberInfoTooltipManager<PsiMember, MemberInfo> myTooltipManager =
      new MemberInfoTooltipManager<>(new MemberInfoTooltipManager.TooltipProvider<>() {
        @Override
        public String getTooltip(MemberInfo memberInfo) {
          if (isMemberEnabled(memberInfo)) return null;
          if (!(memberInfo.getMember() instanceof PsiField field)) return JavaBundle.message("generate.equals.hashcode.internal.error");
          final PsiType type = field.getType();
          if (!(type instanceof PsiArrayType) || JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_5)) return null;
          return JavaBundle.message("generate.equals.hashcode.warning.hashcode.for.arrays.is.not.supported");
        }
      });

    @Override
    public boolean isMemberEnabled(MemberInfo member) {
      final PsiMember psiMember = member.getMember();
      return psiMember instanceof PsiField;
    }

    @Override
    public String getTooltipText(MemberInfo member) {
      return myTooltipManager.getTooltip(member);
    }
  }

  private final class TemplateChooserStep extends StepAdapter {
    private final JComponent myPanel;
    private final ComboBox<String> myComboBox;
    private @Nullable Set<String> myInvalidTemplates = null;

    private TemplateChooserStep(PsiClass psiClass) {
      myPanel = new JPanel(new VerticalFlowLayout());
      final JPanel templateChooserPanel = new JPanel(new BorderLayout());
      final JLabel templateChooserLabel = new JLabel(JavaBundle.message("generate.equals.hashcode.template"));
      templateChooserPanel.add(templateChooserLabel, BorderLayout.WEST);

      EqualsHashCodeTemplatesManager templatesManager = EqualsHashCodeTemplatesManager.getInstance();
      Collection<TemplateResource> templates = templatesManager.getAllTemplates();
      myComboBox = new ComboBox<>(templates.stream()
        .map(EqualsHashCodeTemplatesManager::getTemplateBaseName)
        .distinct()
        .toArray(String[]::new));
      myComboBox.setSelectedItem(templatesManager.getDefaultTemplateBaseName());
      myComboBox.setSwingPopup(false);
      final ComponentWithBrowseButton<ComboBox<?>> comboBoxWithBrowseButton =
        new ComponentWithBrowseButton<>(myComboBox, e -> {
          EqualsHashCodeTemplatesPanel ui = new EqualsHashCodeTemplatesPanel(psiClass.getProject(), templatesManager);
          ui.selectNodeInTree(templatesManager.getDefaultTemplateBaseName());
          ShowSettingsUtil.getInstance().editConfigurable(myPanel, ui);
          String[] names = templatesManager.getAllTemplates().stream()
            .map(EqualsHashCodeTemplatesManager::getTemplateBaseName)
            .distinct()
            .toArray(String[]::new);
          myComboBox.setModel(new DefaultComboBoxModel<>(names));
          myComboBox.setSelectedItem(templatesManager.getDefaultTemplateBaseName());
        });
      templateChooserLabel.setLabelFor(myComboBox);
      ReadAction.nonBlocking(() -> {
          GlobalSearchScope resolveScope = psiClass.getResolveScope();
          JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(psiClass.getProject());
          Set<String> names = new LinkedHashSet<>();
          Set<String> invalid = new HashSet<>();

          DumbService dumbService = DumbService.getInstance(psiClass.getProject());
          for (TemplateResource resource : templatesManager.getAllTemplates()) {
            String templateBaseName = EqualsHashCodeTemplatesManager.getTemplateBaseName(resource);
            if (names.add(templateBaseName)) {
              String className = resource.getClassName();
              if (className != null &&
                  dumbService.computeWithAlternativeResolveEnabled(() -> psiFacade.findClass(className, resolveScope) == null)) {
                invalid.add(templateBaseName);
              }
            }
          }
          return invalid;
        })
        .expireWhen(() -> isDisposed())
        .finishOnUiThread(ModalityState.any(), invalid -> {
          myInvalidTemplates = invalid;
          updateErrorMessage();
          myComboBox.repaint();
        })
        .submit(AppExecutorUtil.getAppExecutorService());
      myComboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
        label.setText(value); //NON-NLS
        if (myInvalidTemplates != null && myInvalidTemplates.contains(value)) {
          label.setForeground(JBColor.RED);
        }
      }));
      myComboBox.addActionListener(e -> updateErrorMessage());

      templateChooserPanel.add(comboBoxWithBrowseButton, BorderLayout.CENTER);
      myPanel.add(templateChooserPanel);

      boolean useInstanceof = CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER;
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
      JLabel label = new JLabel(JavaBundle.message("generate.equals.hashcode.type.comparison.label"));
      label.setBorder(JBUI.Borders.emptyTop(UIUtil.LARGE_VGAP));
      panel.add(label);
      ContextHelpLabel contextHelp = ContextHelpLabel.create(JavaBundle.message("generate.equals.hashcode.comparison.table"));
      contextHelp.setBorder(JBUI.Borders.empty(UIUtil.LARGE_VGAP, 2, 0, 0));
      panel.add(contextHelp);
      JRadioButton instanceofButton =
        new JRadioButton(JavaBundle.message("generate.equals.hashcode.instanceof.type.comparison"), useInstanceof);
      instanceofButton.setBorder(JBUI.Borders.emptyLeft(16));
      JRadioButton getClassButton =
        new JRadioButton(JavaBundle.message("generate.equals.hashcode.getclass.type.comparison"), !useInstanceof);
      getClassButton.setBorder(JBUI.Borders.emptyLeft(16));
      ButtonGroup group = new ButtonGroup();
      group.add(instanceofButton);
      group.add(getClassButton);
      instanceofButton.addActionListener(e -> CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER = true);
      getClassButton.addActionListener(e -> CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER = false);
      myPanel.add(panel);
      myPanel.add(instanceofButton);
      myPanel.add(getClassButton);

      final JCheckBox gettersCheckbox = new NonFocusableCheckBox(JavaBundle.message("generate.equals.hashcode.use.getters"));
      gettersCheckbox.setBorder(JBUI.Borders.emptyTop(UIUtil.LARGE_VGAP));
      gettersCheckbox.setSelected(CodeInsightSettings.getInstance().USE_ACCESSORS_IN_EQUALS_HASHCODE);
      gettersCheckbox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(@NotNull ActionEvent e) {
          CodeInsightSettings.getInstance().USE_ACCESSORS_IN_EQUALS_HASHCODE = gettersCheckbox.isSelected();
        }
      });
      myPanel.add(gettersCheckbox);
    }

    @Override
    public void _commit(boolean finishChosen) throws CommitStepException {
      EqualsHashCodeTemplatesManager.getInstance().setDefaultTemplate((String)myComboBox.getSelectedItem());
      super._commit(finishChosen);
    }

    private void updateErrorMessage() {
      String item = (String)myComboBox.getSelectedItem();
      if (myInvalidTemplates != null && myInvalidTemplates.contains(item)) {
        TemplateResource template =
          EqualsHashCodeTemplatesManager.getInstance().findTemplateByName(EqualsHashCodeTemplatesManager.toEqualsName(item));
        if (template != null) {
          String className = template.getClassName();
          setErrorText(className != null ? JavaBundle.message("dialog.message.class.not.found", className)
                                         : JavaBundle.message("dialog.message.template.not.applicable"), myComboBox);
        }
        else {
          setErrorText(JavaBundle.message("dialog.message.template.not.found"), myComboBox);
        }
      }
      else {
        setErrorText(null, myComboBox);
      }
    }

    @Override
    public JComponent getComponent() {
      return myPanel;
    }
  }
}
