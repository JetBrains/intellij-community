package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * @author Dmitry Batkovich
 */
public class JavaOverrideImplementMemberChooser extends MemberChooser<PsiMethodMember> {
  private static final String SORT_METHODS_BY_PERCENT_DESCRIPTION = "Sort by Percent of Classes which Overrides a Method";

  @NonNls public static final String PROP_COMBINED_OVERRIDE_IMPLEMENT = "OverrideImplement.combined";
  @NonNls public static final String PROP_OVERRIDING_SORTED_OVERRIDE_IMPLEMENT = "OverrideImplement.overriding.sorted";

  private ToggleAction mySortByOverridingAction;
  private ToggleAction myMergeAction;
  private final PsiMethodMember[] myAllElements;
  private final PsiMethodMember[] myOnlyPrimaryElements;
  private final NotNullLazyValue<PsiMethodWithOverridingPercentMember[]> myLazyElementsWithPercent;
  private final boolean myToImplement;
  private Project myProject;
  private boolean myMerge;
  private boolean mySortedByOverriding;

  @Nullable
  public static JavaOverrideImplementMemberChooser create(final PsiElement aClass,
                                                          final boolean toImplement,
                                                          final Collection<CandidateInfo> candidates,
                                                          final Collection<CandidateInfo> secondary) {
    final Project project = aClass.getProject();
    if (candidates.isEmpty() && secondary.isEmpty()) return null;

    final PsiMethodMember[] onlyPrimary = convertToMethodMembers(candidates);
    final LinkedHashSet<CandidateInfo> allCandidates = new LinkedHashSet<CandidateInfo>(candidates);
    allCandidates.addAll(secondary);
    final PsiMethodMember[] all = convertToMethodMembers(allCandidates);
    final NotNullLazyValue<PsiMethodWithOverridingPercentMember[]> lazyElementsWithPercent =
      new NotNullLazyValue<PsiMethodWithOverridingPercentMember[]>() {
        @NotNull
        @Override
        protected PsiMethodWithOverridingPercentMember[] compute() {
          final PsiMethodWithOverridingPercentMember[] elements =
            PsiMethodWithOverridingPercentMember.calculateOverridingPercents(candidates);
          Arrays.sort(elements, PsiMethodWithOverridingPercentMember.COMPARATOR);
          return elements;
        }
      };
    final boolean merge = PropertiesComponent.getInstance(project).getBoolean(PROP_COMBINED_OVERRIDE_IMPLEMENT, true);
    final JavaOverrideImplementMemberChooser javaOverrideImplementMemberChooser =
      new JavaOverrideImplementMemberChooser(all, onlyPrimary, lazyElementsWithPercent, project, PsiUtil.isLanguageLevel5OrHigher(aClass),
                                             merge, toImplement, PropertiesComponent.getInstance(project)
        .getBoolean(PROP_OVERRIDING_SORTED_OVERRIDE_IMPLEMENT, false));
    javaOverrideImplementMemberChooser.setTitle(getChooserTitle(toImplement, merge));

    javaOverrideImplementMemberChooser.setCopyJavadocVisible(true);

    if (toImplement) {
      if (onlyPrimary.length == 0) {
        javaOverrideImplementMemberChooser.selectElements(new ClassMember[] {all[0]});
      } else {
        javaOverrideImplementMemberChooser.selectElements(onlyPrimary);
      }
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (!toImplement || onlyPrimary.length == 0) {
        javaOverrideImplementMemberChooser.selectElements(all);
      }
      javaOverrideImplementMemberChooser.close(DialogWrapper.OK_EXIT_CODE);
      return javaOverrideImplementMemberChooser;
    }
    return javaOverrideImplementMemberChooser;
  }

  private JavaOverrideImplementMemberChooser(final PsiMethodMember[] allElements,
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
    myMerge = merge;
    myToImplement = toImplement;
    mySortedByOverriding = sortedByOverriding;
    resetElements(getInitialElements(allElements, onlyPrimaryElements, lazyElementsWithPercent, merge, toImplement, sortedByOverriding));
    init();
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
    resetElements(myToImplement || myMerge ? myAllElements : myOnlyPrimaryElements, null, true);
    if (mySortByOverridingAction != null) {
      mySortByOverridingAction.setSelected(event, false);
    }
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    PropertiesComponent.getInstance(myProject).setValue(PROP_COMBINED_OVERRIDE_IMPLEMENT, String.valueOf(myMerge));
    PropertiesComponent.getInstance(myProject).setValue(PROP_OVERRIDING_SORTED_OVERRIDE_IMPLEMENT, String.valueOf(mySortedByOverriding));
  }

  @Override
  protected void fillToolbarActions(DefaultActionGroup group) {
    super.fillToolbarActions(group);
    if (myToImplement) return;

    mySortByOverridingAction = new MySortByOverridingAction();
    if (mySortedByOverriding) {
      changeSortComparator(PsiMethodWithOverridingPercentMember.COMPARATOR);
    }
    group.add(mySortByOverridingAction, Constraints.FIRST);

    myMergeAction = new MyMergeAction();
    group.add(myMergeAction);
  }

  private static String getChooserTitle(final boolean toImplement, final boolean merge) {
    return toImplement
           ? CodeInsightBundle.message("methods.to.implement.chooser.title")
           : merge
             ? CodeInsightBundle.message("methods.to.override.implement.chooser.title")
             : CodeInsightBundle.message("methods.to.override.chooser.title");
  }

  private static PsiMethodMember[] convertToMethodMembers(Collection<CandidateInfo> candidates) {
    return ContainerUtil.map2Array(candidates, PsiMethodMember.class, new Function<CandidateInfo, PsiMethodMember>() {
      @Override
      public PsiMethodMember fun(final CandidateInfo s) {
        return new PsiMethodMember(s);
      }
    });
  }

  private class MySortByOverridingAction extends ToggleAction {
    public MySortByOverridingAction() {
      super(SORT_METHODS_BY_PERCENT_DESCRIPTION, SORT_METHODS_BY_PERCENT_DESCRIPTION, AllIcons.ObjectBrowser.SortedByUsage);
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.ALT_MASK)), myTree);
    }

    @Override
    public boolean isSelected(final AnActionEvent e) {
      return mySortedByOverriding;
    }

    @Override
    public void setSelected(final AnActionEvent e, final boolean state) {
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
        resetElementsWithDefaultComparator(elementsToRender, true);
      }
    }
  }

  private class MyMergeAction extends ToggleAction {
    private MyMergeAction() {
      super("Show methods to implement", "Show methods to implement", AllIcons.General.Show_to_implement);
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.ALT_MASK)), myTree);
      final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts("OverrideMethods");
      registerCustomShortcutSet(new CustomShortcutSet(shortcuts), myTree);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myMerge;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myMerge = state;
      if (state && mySortByOverridingAction.isSelected(e)) {
        mySortByOverridingAction.setSelected(e, false);
      }
      resetElements(state ? myAllElements : myOnlyPrimaryElements, null, true);
      setTitle(getChooserTitle(false, myMerge));
    }
  }

}
