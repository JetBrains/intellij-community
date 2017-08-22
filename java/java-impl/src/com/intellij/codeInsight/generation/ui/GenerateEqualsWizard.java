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
package com.intellij.codeInsight.generation.ui;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManager;
import com.intellij.codeInsight.generation.GenerateEqualsHelper;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.classMembers.AbstractMemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.classMembers.MemberInfoTooltipManager;
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.psi.PsiAdapter;
import org.jetbrains.java.generate.template.TemplateResource;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author dsl
 */
public class GenerateEqualsWizard extends AbstractGenerateEqualsWizard<PsiClass, PsiMember, MemberInfo> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.ui.GenerateEqualsWizard");

  private static final MyMemberInfoFilter MEMBER_INFO_FILTER = new MyMemberInfoFilter();

  public static class JavaGenerateEqualsWizardBuilder extends AbstractGenerateEqualsWizard.Builder<PsiClass, PsiMember, MemberInfo> {
    private final PsiClass myClass;

    private final MemberSelectionPanel myEqualsPanel;
    private final MemberSelectionPanel myHashCodePanel;
    private final MemberSelectionPanel myNonNullPanel;
    private final HashMap<PsiMember, MemberInfo> myFieldsToHashCode;
    private final HashMap<PsiMember, MemberInfo> myFieldsToNonNull;
    private final List<MemberInfo> myClassFields;

    private JavaGenerateEqualsWizardBuilder(PsiClass aClass, boolean needEquals, boolean needHashCode) {
      LOG.assertTrue(needEquals || needHashCode);
      myClass = aClass;
      myClassFields = MemberInfo.extractClassMembers(myClass, MEMBER_INFO_FILTER, false);
      for (MemberInfo myClassField : myClassFields) {
        myClassField.setChecked(true);
      }
      if (needEquals) {
        myEqualsPanel = new MemberSelectionPanel(CodeInsightBundle.message("generate.equals.hashcode.equals.fields.chooser.title"),
                                                 myClassFields, null);
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
        myHashCodePanel = new MemberSelectionPanel(CodeInsightBundle.message("generate.equals.hashcode.hashcode.fields.chooser.title"), hashCodeMemberInfos, null);
        myHashCodePanel.getTable().setMemberInfoModel(new HashCodeMemberInfoModel());
        if (needEquals) {
          updateHashCodeMemberInfos(myClassFields);
        }
      }
      else {
        myHashCodePanel = null;
        myFieldsToHashCode = null;
      }
      myNonNullPanel = new MemberSelectionPanel(CodeInsightBundle.message("generate.equals.hashcode.non.null.fields.chooser.title"), Collections.emptyList(), null);
      myFieldsToNonNull = createFieldToMemberInfoMap(false);
      for (final Map.Entry<PsiMember, MemberInfo> entry : myFieldsToNonNull.entrySet()) {
        entry.getValue().setChecked(NullableNotNullManager.isNotNull(entry.getKey()));
      }
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
    protected void updateHashCodeMemberInfos(Collection<MemberInfo> equalsMemberInfos) {
      if (myHashCodePanel == null) return;
      List<MemberInfo> hashCodeFields = new ArrayList<>();

      for (MemberInfo equalsMemberInfo : equalsMemberInfos) {
        hashCodeFields.add(myFieldsToHashCode.get(equalsMemberInfo.getMember()));
      }

      myHashCodePanel.getTable().setMemberInfos(hashCodeFields);
    }

    @Override
    protected void updateNonNullMemberInfos(Collection<MemberInfo> equalsMemberInfos) {
      final ArrayList<MemberInfo> list = new ArrayList<>();

      for (MemberInfo equalsMemberInfo : equalsMemberInfos) {
        PsiField field = (PsiField)equalsMemberInfo.getMember();
        if (!(field.getType() instanceof PsiPrimitiveType)) {
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

  public GenerateEqualsWizard(Project project, PsiClass aClass, boolean needEquals, boolean needHashCode) {
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

  private static PsiField[] memberInfosToFields(Collection<MemberInfo> infos) {
    ArrayList<PsiField> list = new ArrayList<>();
    for (MemberInfo info : infos) {
      list.add((PsiField)info.getMember());
    }
    return list.toArray(new PsiField[list.size()]);
  }

  @Override
  protected String getHelpID() {
    return "editing.altInsert.equals";
  }

  private void equalsFieldsSelected() {
    Collection<MemberInfo> selectedMemberInfos = myEqualsPanel.getTable().getSelectedMemberInfos();
    updateHashCodeMemberInfos(selectedMemberInfos);
    updateNonNullMemberInfos(selectedMemberInfos);
  }

  @Override
  protected void doOKAction() {
    if (myEqualsPanel != null) {
      equalsFieldsSelected();
    }
    super.doOKAction();
  }

  @Override
  protected int getNextStep(int step) {
    if (step + 1 == getNonNullStepCode()) {
      if (templateDependsOnFieldsNullability()) {
        for (MemberInfo classField : myClassFields) {
          if (classField.isChecked()) {
            PsiField field = (PsiField)classField.getMember();
            if (!(field.getType() instanceof PsiPrimitiveType)) {
              return getNonNullStepCode();
            }
          }
        }
      }
      return step;
    }

    return super.getNextStep(step);
  }

  private static boolean templateDependsOnFieldsNullability() {
    final EqualsHashCodeTemplatesManager templatesManager = EqualsHashCodeTemplatesManager.getInstance();
    final String notNullCheckPresent = "\\.notNull[^\\w]";
    final Pattern pattern = Pattern.compile(notNullCheckPresent);
    return pattern.matcher(templatesManager.getDefaultEqualsTemplate().getTemplate()).find() ||
           pattern.matcher(templatesManager.getDefaultHashcodeTemplate().getTemplate()).find();
  }

  @Override
  protected void addSteps() {
    if (myEqualsPanel != null) {
      addStep(new TemplateChooserStep(myClass.hasModifierProperty(PsiModifier.FINAL), myClass));
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
      new MemberInfoTooltipManager<>(new MemberInfoTooltipManager.TooltipProvider<PsiMember, MemberInfo>() {
        @Override
        public String getTooltip(MemberInfo memberInfo) {
          if (checkForProblems(memberInfo) == OK) return null;
          if (!(memberInfo.getMember() instanceof PsiField)) return CodeInsightBundle.message("generate.equals.hashcode.internal.error");
          final PsiField field = (PsiField)memberInfo.getMember();
          if (!JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_5)) {
            final PsiType type = field.getType();
            if (PsiAdapter.isNestedArray(type)) {
              return CodeInsightBundle.message("generate.equals.warning.equals.for.nested.arrays.not.supported");
            }
            if (GenerateEqualsHelper.isArrayOfObjects(type)) {
              return CodeInsightBundle.message("generate.equals.warning.generated.equals.could.be.incorrect");
            }
          }
          return null;
        }
      });

    @Override
    public boolean isMemberEnabled(MemberInfo member) {
      if (!(member.getMember() instanceof PsiField)) return false;
      final PsiField field = (PsiField)member.getMember();
      final PsiType type = field.getType();
      return JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_5) || !PsiAdapter.isNestedArray(type);
    }

    @Override
    public int checkForProblems(@NotNull MemberInfo member) {
      if (!(member.getMember() instanceof PsiField)) return ERROR;
      final PsiField field = (PsiField)member.getMember();
      final PsiType type = field.getType();
      if (!JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_5)) {
        if (PsiAdapter.isNestedArray(type)) return ERROR;
        if (GenerateEqualsHelper.isArrayOfObjects(type)) return WARNING;
      }
      return OK;
    }

    @Override
    public String getTooltipText(MemberInfo member) {
      return myTooltipManager.getTooltip(member);
    }
  }

  private static class HashCodeMemberInfoModel extends AbstractMemberInfoModel<PsiMember, MemberInfo> {
    private final MemberInfoTooltipManager<PsiMember, MemberInfo> myTooltipManager =
      new MemberInfoTooltipManager<>(new MemberInfoTooltipManager.TooltipProvider<PsiMember, MemberInfo>() {
        @Override
        public String getTooltip(MemberInfo memberInfo) {
          if (isMemberEnabled(memberInfo)) return null;
          if (!(memberInfo.getMember() instanceof PsiField)) return CodeInsightBundle.message("generate.equals.hashcode.internal.error");
          final PsiField field = (PsiField)memberInfo.getMember();
          final PsiType type = field.getType();
          if (!(type instanceof PsiArrayType) || JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_5)) return null;
          return CodeInsightBundle.message("generate.equals.hashcode.warning.hashcode.for.arrays.is.not.supported");
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

  private static class TemplateChooserStep extends StepAdapter {
    private final JComponent myPanel;

    private TemplateChooserStep(boolean isFinal, PsiClass psiClass) {
      myPanel = new JPanel(new VerticalFlowLayout());
      final JPanel templateChooserPanel = new JPanel(new BorderLayout());
      final JLabel templateChooserLabel = new JLabel(CodeInsightBundle.message("generate.equals.hashcode.template"));
      templateChooserPanel.add(templateChooserLabel, BorderLayout.WEST);

    
      final ComboBox<String> comboBox = new ComboBox<>();
      final ComponentWithBrowseButton<ComboBox> comboBoxWithBrowseButton =
        new ComponentWithBrowseButton<>(comboBox, new MyEditTemplatesListener(psiClass, myPanel, comboBox));
      templateChooserLabel.setLabelFor(comboBox);
      final EqualsHashCodeTemplatesManager manager = EqualsHashCodeTemplatesManager.getInstance();
      setupCombobox(manager, comboBox, psiClass);
      comboBox.addActionListener(new ActionListener() {
        public void actionPerformed(@NotNull final ActionEvent M) {
          manager.setDefaultTemplate((String)comboBox.getSelectedItem());
        }
      });

      templateChooserPanel.add(comboBoxWithBrowseButton, BorderLayout.CENTER);
      myPanel.add(templateChooserPanel);

      final JCheckBox checkbox = new NonFocusableCheckBox(CodeInsightBundle.message("generate.equals.hashcode.accept.sublcasses"));
      checkbox.setSelected(!isFinal && CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER);
      checkbox.setEnabled(!isFinal);
      checkbox.addActionListener(new ActionListener() {
        public void actionPerformed(@NotNull final ActionEvent M) {
          CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER = checkbox.isSelected();
        }
      });
      myPanel.add(checkbox);
      myPanel.add(new JLabel(CodeInsightBundle.message("generate.equals.hashcode.accept.sublcasses.explanation")));

      final JCheckBox gettersCheckbox = new NonFocusableCheckBox(CodeInsightBundle.message("generate.equals.hashcode.use.getters"));
      gettersCheckbox.setSelected(CodeInsightSettings.getInstance().USE_ACCESSORS_IN_EQUALS_HASHCODE);
      gettersCheckbox.addActionListener(new ActionListener() {
        public void actionPerformed(@NotNull final ActionEvent M) {
          CodeInsightSettings.getInstance().USE_ACCESSORS_IN_EQUALS_HASHCODE = gettersCheckbox.isSelected();
        }
      });
      myPanel.add(gettersCheckbox);
    }

    @Override
    public JComponent getComponent() {
      return myPanel;
    }

    private static void setupCombobox(EqualsHashCodeTemplatesManager templatesManager, 
                                      ComboBox<String> comboBox,
                                      PsiClass psiClass) {
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(psiClass.getProject());
      final GlobalSearchScope resolveScope = psiClass.getResolveScope();
      final Set<String> names = new LinkedHashSet<>();
      
      final Set<String> invalid = new HashSet<>();
      for (TemplateResource resource : templatesManager.getAllTemplates()) {
        final String templateBaseName = EqualsHashCodeTemplatesManager.getTemplateBaseName(resource);
        if (names.add(templateBaseName)) {
          final String className = resource.getClassName();
          if (className != null && psiFacade.findClass(className, resolveScope) == null) {
            invalid.add(templateBaseName);
          }    
        }
      }
      comboBox.setRenderer(new ListCellRendererWrapper<String>() {
        @Override
        public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
          setText(value);
          if (invalid.contains(value)) {
            setForeground(JBColor.RED);
          }
        }
      });
      comboBox.setModel(new DefaultComboBoxModel<>(ArrayUtil.toStringArray(names)));
      String baseName = templatesManager.getDefaultTemplateBaseName();
      if (invalid.contains(baseName)) { //preselect default template but do not remember as default
        baseName = EqualsHashCodeTemplatesManager.getTemplateBaseName(templatesManager.getAllTemplates().iterator().next());
      }
      comboBox.setSelectedItem(baseName);
    }

    private static class MyEditTemplatesListener implements ActionListener {
      private final PsiClass myPsiClass;
      private final JComponent myParent;
      private final ComboBox<String> myComboBox;

      public MyEditTemplatesListener(PsiClass psiClass, JComponent panel, ComboBox<String> comboBox) {
        myPsiClass = psiClass;
        myParent = panel;
        myComboBox = comboBox;
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        final EqualsHashCodeTemplatesManager templatesManager = EqualsHashCodeTemplatesManager.getInstance();
        final EqualsHashCodeTemplatesPanel ui = new EqualsHashCodeTemplatesPanel(myPsiClass.getProject(), EqualsHashCodeTemplatesManager.getInstance());
        ui.selectNodeInTree(templatesManager.getDefaultTemplateBaseName());
        ShowSettingsUtil.getInstance().editConfigurable(myParent, ui);
        setupCombobox(templatesManager, myComboBox, myPsiClass);
      }
    }
  }
}
