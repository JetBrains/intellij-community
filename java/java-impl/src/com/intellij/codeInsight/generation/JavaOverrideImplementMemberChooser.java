// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

/**
 * @author Dmitry Batkovich
 */
public final class JavaOverrideImplementMemberChooser extends MemberChooser<PsiMethodMember> {
  @NonNls public static final String PROP_COMBINED_OVERRIDE_IMPLEMENT = "OverrideImplement.combined";
  @NonNls public static final String PROP_OVERRIDING_SORTED_OVERRIDE_IMPLEMENT = "OverrideImplement.overriding.sorted";
  @NonNls public static final String PROP_GENERATE_JAVADOC_OVERRIDE_IMPLEMENT = "OverrideImplement.generate.javadoc";

  private ToggleAction myMergeAction;
  private final PsiMethodMember[] myAllElements;
  private final PsiMethodMember[] myOnlyPrimaryElements;
  private final NotNullLazyValue<PsiMethodWithOverridingPercentMember[]> myLazyElementsWithPercent;
  private final boolean myToImplement;
  private final Project myProject;
  private final PsiFile myFile;
  private boolean myMerge;
  private boolean mySortedByOverriding;
  private JBCheckBox myGenerateJavadocCheckBox;

  @Nullable
  public static JavaOverrideImplementMemberChooser create(final PsiElement aClass,
                                                          final boolean toImplement,
                                                          final Collection<? extends CandidateInfo> candidates,
                                                          final Collection<? extends CandidateInfo> secondary) {
    JavaOverrideImplementMemberChooserContainer result = prepare(aClass, toImplement, candidates, secondary);
    if (result == null) return null;
    return create(result);
  }

  @NotNull
  public static JavaOverrideImplementMemberChooser create(@NotNull JavaOverrideImplementMemberChooserContainer container) {
    final JavaOverrideImplementMemberChooser javaOverrideImplementMemberChooser =
      new JavaOverrideImplementMemberChooser(container.file(), container.all(), container.onlyPrimary(),
                                             container.lazyElementsWithPercent(),
                                             container.project(), container.overrideVisible(),
                                             container.merge(), container.toImplement(),
                                             PropertiesComponent.getInstance(container.project())
                                               .getBoolean(PROP_OVERRIDING_SORTED_OVERRIDE_IMPLEMENT));
    javaOverrideImplementMemberChooser.setTitle(getChooserTitle(container.toImplement(), container.merge()));

    javaOverrideImplementMemberChooser.setCopyJavadocVisible(true);

    if (container.selectElements() != null) {
      javaOverrideImplementMemberChooser.selectElements(container.selectElements());
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (!container.toImplement() || container.onlyPrimary().length == 0) {
        javaOverrideImplementMemberChooser.selectElements(container.all());
      }
      javaOverrideImplementMemberChooser.close(DialogWrapper.OK_EXIT_CODE);
      return javaOverrideImplementMemberChooser;
    }
    return javaOverrideImplementMemberChooser;
  }

  @Nullable
  public static JavaOverrideImplementMemberChooserContainer prepare(PsiElement aClass,
                                                                    boolean toImplement,
                                                                    Collection<? extends CandidateInfo> candidates,
                                                                    Collection<? extends CandidateInfo> secondary) {
    final Project project = aClass.getProject();
    final PsiFile file = aClass.getContainingFile();
    if (file == null) {
      return null;
    }
    if (candidates.isEmpty() && secondary.isEmpty()) return null;

    final PsiMethodMember[] onlyPrimary = convertToMethodMembers(candidates);
    final LinkedHashSet<CandidateInfo> allCandidates = new LinkedHashSet<>(candidates);
    allCandidates.addAll(secondary);
    final PsiMethodMember[] all = convertToMethodMembers(allCandidates);
    final NotNullLazyValue<PsiMethodWithOverridingPercentMember[]> lazyElementsWithPercent = NotNullLazyValue.lazy(() -> {
      final PsiMethodWithOverridingPercentMember[] elements =
        PsiMethodWithOverridingPercentMember.calculateOverridingPercents(candidates);
      Arrays.sort(elements, PsiMethodWithOverridingPercentMember.COMPARATOR);
      return elements;
    });
    final boolean merge = PropertiesComponent.getInstance(project).getBoolean(PROP_COMBINED_OVERRIDE_IMPLEMENT, true);

    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(aClass);
    //hide option if implement interface for 1.5 language level
    final boolean overrideVisible =
      languageLevel.isAtLeast(LanguageLevel.JDK_1_6) || languageLevel.equals(LanguageLevel.JDK_1_5) && !toImplement;

    ClassMember[] selectElements = null;
    if (toImplement) {
      if (onlyPrimary.length == 0) {
        selectElements = new ClassMember[]{all[0]};
      }
      else {
        PsiClass currClass = ObjectUtils.tryCast(aClass, PsiClass.class);
        if (currClass != null && currClass.isRecord()) {
          PsiMethodMember[] toImplementMembers = ContainerUtil
            .filter(onlyPrimary, m -> !OverrideImplementExploreUtil.belongsToRecord(m.getElement()))
            .toArray(new PsiMethodMember[0]);
          selectElements = ArrayUtil.isEmpty(toImplementMembers) ? onlyPrimary : toImplementMembers;
        }
        else {
          selectElements = onlyPrimary;
        }
      }
    }
    return new JavaOverrideImplementMemberChooserContainer(project, file, onlyPrimary, all, lazyElementsWithPercent, merge, overrideVisible,
                                                           toImplement, selectElements);
  }

  public record JavaOverrideImplementMemberChooserContainer(@NotNull Project project,
                                                            @NotNull PsiFile file,
                                                            PsiMethodMember @NotNull [] onlyPrimary,
                                                            PsiMethodMember @NotNull [] all,
                                                            @NotNull NotNullLazyValue<PsiMethodWithOverridingPercentMember[]> lazyElementsWithPercent,
                                                            boolean merge,
                                                            boolean overrideVisible,
                                                            boolean toImplement,
                                                            ClassMember @Nullable[] selectElements) {
  }

  private JavaOverrideImplementMemberChooser(final @NotNull PsiFile file,
                                             final PsiMethodMember[] allElements,
                                             final PsiMethodMember[] onlyPrimaryElements,
                                             final NotNullLazyValue<PsiMethodWithOverridingPercentMember[]> lazyElementsWithPercent,
                                             final @NotNull Project project,
                                             final boolean isInsertOverrideVisible,
                                             final boolean merge,
                                             final boolean toImplement,
                                             final boolean sortedByOverriding) {
    super(false, true, project, isInsertOverrideVisible, null, null);
    myAllElements = allElements;
    myOnlyPrimaryElements = onlyPrimaryElements;
    myLazyElementsWithPercent = lazyElementsWithPercent;
    myProject = project;
    myFile = file;
    myMerge = merge;
    myToImplement = toImplement;
    mySortedByOverriding = sortedByOverriding;
    resetElements(getInitialElements(allElements, onlyPrimaryElements, lazyElementsWithPercent, merge, toImplement, sortedByOverriding));
    init();
  }

  @Override
  public void resetElements(PsiMethodMember[] elements) {
    super.resetElements(elements);
    if (myOptionControls.length > 0 && myFile.getLanguage().is(JavaLanguage.INSTANCE)) {
      myGenerateJavadocCheckBox = new JBCheckBox(JavaBundle.message("methods.to.override.generate.javadoc"));
      myGenerateJavadocCheckBox.setSelected(isGenerateJavadoc());
      myOptionControls = ArrayUtil.insert(super.getOptionControls(), 1, myGenerateJavadocCheckBox);
    }
  }

  @Override
  protected void customizeOptionsPanel() {
    super.customizeOptionsPanel();
    if (myGenerateJavadocCheckBox != null) {
      myGenerateJavadocCheckBox.setSelected(isGenerateJavadoc());
    }
  }

  public boolean isGenerateJavadoc(){
    return PropertiesComponent.getInstance(myProject).getBoolean(PROP_GENERATE_JAVADOC_OVERRIDE_IMPLEMENT, false);
  }

  private static PsiMethodMember[] getInitialElements(PsiMethodMember[] allElements,
                                                      PsiMethodMember[] onlyPrimaryElements,
                                                      NotNullLazyValue<PsiMethodWithOverridingPercentMember[]> lazyElementsWithPercent,
                                                      boolean merge,
                                                      boolean toImplement,
                                                      boolean sortByOverriding) {
    final boolean showElementsWithPercents = sortByOverriding && !toImplement;
    final PsiMethodMember[] defaultElements = toImplement || merge ? allElements : onlyPrimaryElements;
    return showElementsWithPercents ? lazyElementsWithPercent.getValue() : defaultElements;
  }


  @Override
  protected void onAlphabeticalSortingEnabled(final AnActionEvent event) {
    mySortedByOverriding = false;
    resetElements(myToImplement || myMerge ? myAllElements : myOnlyPrimaryElements, null, true);
    restoreTree();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    PropertiesComponent.getInstance(myProject).setValue(PROP_COMBINED_OVERRIDE_IMPLEMENT, myMerge, true);
    PropertiesComponent.getInstance(myProject).setValue(PROP_OVERRIDING_SORTED_OVERRIDE_IMPLEMENT, mySortedByOverriding);
    if (myGenerateJavadocCheckBox != null) {
      PropertiesComponent.getInstance(myProject).setValue(PROP_GENERATE_JAVADOC_OVERRIDE_IMPLEMENT, myGenerateJavadocCheckBox.isSelected());
    }
  }

  @Override
  protected void fillToolbarActions(DefaultActionGroup group) {
    super.fillToolbarActions(group);
    if (myToImplement) return;

    ToggleAction sortByOverridingAction = new MySortByOverridingAction();
    if (mySortedByOverriding) {
      changeSortComparator(PsiMethodWithOverridingPercentMember.COMPARATOR);
    }
    group.add(sortByOverridingAction, Constraints.FIRST);

    myMergeAction = new MyMergeAction();
    group.add(myMergeAction);
  }

  static @NlsContexts.DialogTitle String getChooserTitle(final boolean toImplement, final boolean merge) {
    return toImplement
           ? JavaBundle.message("methods.to.implement.chooser.title")
           : merge
             ? JavaBundle.message("methods.to.override.implement.chooser.title")
             : JavaBundle.message("methods.to.override.chooser.title");
  }

  private static PsiMethodMember[] convertToMethodMembers(Collection<? extends CandidateInfo> candidates) {
    return ContainerUtil.map2Array(candidates, PsiMethodMember.class, s -> new PsiMethodMember(s));
  }

  @Override
  protected boolean isInsertOverrideAnnotationSelected() {
    return JavaCodeStyleSettings.getInstance(myFile).INSERT_OVERRIDE_ANNOTATION;
  }

  private class MySortByOverridingAction extends ToggleAction {
    MySortByOverridingAction() {
      super(JavaBundle.message("action.sort.by.percent.classes.which.overrides.method.text"),
            JavaBundle.message("action.sort.by.percent.classes.which.overrides.method.description"), AllIcons.ObjectBrowser.SortedByUsage);
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.ALT_MASK)), myTree);
    }

    @Override
    public boolean isSelected(@NotNull final AnActionEvent e) {
      return mySortedByOverriding;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull final AnActionEvent e, final boolean state) {
      mySortedByOverriding = state;
      if (state) {
        if (myMerge) {
          myMergeAction.setSelected(e, false);
        }
        disableAlphabeticalSorting(e);
        final PsiMethodWithOverridingPercentMember[] elementsWithPercent = myLazyElementsWithPercent.getValue();
        resetElements(elementsWithPercent, PsiMethodWithOverridingPercentMember.COMPARATOR, false);
      }
      else {
        final PsiMethodMember[] elementsToRender = myMerge ? myAllElements : myOnlyPrimaryElements;
        resetElementsWithDefaultComparator(elementsToRender);
      }
    }
  }

  private final class MyMergeAction extends DumbAwareToggleAction {
    private MyMergeAction() {
      super(JavaBundle.message("action.text.show.methods.to.implement"), JavaBundle.message(
        "action.text.show.methods.to.implement"), AllIcons.General.Show_to_implement);
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.ALT_MASK)), myTree);
      registerCustomShortcutSet(getActiveKeymapShortcuts("OverrideMethods"), myTree);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myMerge;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myMerge = state;
      if (state && mySortedByOverriding) {
        mySortedByOverriding = false;
      }
      resetElements(state ? myAllElements : myOnlyPrimaryElements, null, true);
      restoreTree();
      setTitle(getChooserTitle(false, myMerge));
    }
  }

  public OverrideOrImplementOptions getOptions(){
    return new OverrideOrImplementOptions()
      .copyJavadoc(isCopyJavadoc())
      .generateJavadoc(isGenerateJavadoc())
      .insertOverrideWherePossible(isInsertOverrideAnnotation());
  }
}
