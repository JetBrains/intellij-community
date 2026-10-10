// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * Extension point for the "Safe Delete" refactoring.
 * <p>
 * Delegates are processed one by one.
 * The first delegate which agrees to handle the element ({@link #handlesElement(PsiElement)}) is used, the rest are ignored.
 * The loading order can be changed by providing attribute "order" during registration in plugin.xml.
 */
public interface SafeDeleteProcessorDelegate {
  ExtensionPointName<SafeDeleteProcessorDelegate> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.safeDeleteProcessor");

  /**
   * @return {@code true} if delegates can process {@code element}.
   */
  boolean handlesElement(PsiElement element);

  /**
   * @return the headless safe delete of the elements this delegate handles; null if they cannot be deleted without a user.
   * A subclass inherits it, as it inherits the rest; a subclass whose search differs returns null or its own.
   */
  @ApiStatus.Experimental
  default @Nullable ModPlanSafeDeleteProvider asModPlan() {
    return null;
  }

  /**
   * Find usages of the {@code element} and fill {@code result} with them.
   * Is called during {@link BaseRefactoringProcessor#findUsages()} under modal progress in read action.
   *
   * @param element              an element selected for deletion.
   * @param allElementsToDelete  all elements selected for deletion.
   * @param result               list of {@link UsageInfo} to store found usages
   * @return                     {@code null} if the element should not be searched in text occurrences/comments though corresponding settings were enabled, otherwise
   *                             bean with the information how to detect if an element is inside all elements to delete (e.g. {@link SafeDeleteProcessor#getDefaultInsideDeletedCondition(PsiElement[])})
   *                             and current element.
   */
  @Nullable
  NonCodeUsageSearchInfo findUsages(@NotNull PsiElement element, PsiElement @NotNull [] allElementsToDelete, @NotNull List<? super UsageInfo> result);

  /**
   * Returns elements that are searched for usages of the element selected for deletion. Called before the refactoring dialog is shown.
   * May show UI to ask if additional elements should be deleted along with the specified selected element.
   *
   * @param element an element selected for deletion.
   * @param allElementsToDelete all elements selected for deletion.
   * @return additional elements to search for usages, or null if the user has cancelled the refactoring.
   */
  @Nullable
  Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element, @NotNull Collection<? extends PsiElement> allElementsToDelete);

  /**
   * Returns the list of additional elements to be deleted. Called after the refactoring dialog is shown.
   * May show UI to ask the user if some additional elements should be deleted along with the
   * specified selected element.
   *
   * <p>Avoid overriding this method directly. Instead, override {@link #getAdditionalElementsToDelete(PsiElement, Collection)}
   * to compute additional elements in background.
   *
   * @param element             an element selected for deletion.
   * @param allElementsToDelete all elements selected for deletion.
   * @return additional elements to search for usages, or null if no additional elements were chosen.
   * Returning an empty list is equal to returning null.
   */
  @Nullable
  @ApiStatus.NonExtendable
  default Collection<PsiElement> getAdditionalElementsToDelete(@NotNull PsiElement element,
                                                               @NotNull Collection<? extends PsiElement> allElementsToDelete,
                                                               boolean askUser) {
    Project project = element.getProject();
    AdditionalElementsData data = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.nonBlocking(() -> getAdditionalElementsToDelete(element, allElementsToDelete)).executeSynchronously(),
      RefactoringBundle.message("dialog.title.searching.for.additional.elements.to.delete"), true, project);
    if (data.elements.isEmpty()) return null;
    return switch (data.policy) {
      case ConfirmationPolicy.DontAsk _ -> data.elements;
      case ConfirmationPolicy.Ask ask -> {
        boolean deleteAdditionalElements;
        if (!askUser || ApplicationManager.getApplication().isUnitTestMode()) {
          deleteAdditionalElements = ask.deleteByDefault;
        }
        else {
          deleteAdditionalElements = Messages.showYesNoDialog(project, ask.message, RefactoringBundle.message("safe.delete.title"),
                                                              Messages.getQuestionIcon()) == Messages.YES;
        }
        yield deleteAdditionalElements ? data.elements : null;
      }
    };
  }

  /**
   * Returns the list of additional elements to be deleted. Called in a background read action,
   * after the refactoring dialog is shown.
   *
   * @param element             an element selected for deletion.
   * @param allElementsToDelete all elements selected for deletion.
   * @return an {@link AdditionalElementsData} record containing additional elements to search for usages and
   * the confirmation policy. Default implementation returns {@link AdditionalElementsData#NONE}.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  default @NotNull AdditionalElementsData getAdditionalElementsToDelete(@NotNull PsiElement element,
                                                                        @NotNull Collection<? extends PsiElement> allElementsToDelete) {
    return AdditionalElementsData.NONE;
  }

  /**
   * A container for additional elements to delete
   *
   * @param elements additional elements to delete
   * @param policy confirmation policy to use. Ignored if elements is empty.
   * @see #getAdditionalElementsToDelete(PsiElement, Collection)
   */
  record AdditionalElementsData(@NotNull Collection<@NotNull PsiElement> elements,
                                @NotNull ConfirmationPolicy policy) {
    public AdditionalElementsData(@NotNull Collection<@NotNull PsiElement> elements) {
      this(elements, ConfirmationPolicy.DONT_ASK);
    }

    /**
     * A constant that represents the absense of additional elements to delete.
     */
    public static final AdditionalElementsData NONE = new AdditionalElementsData(List.of(), ConfirmationPolicy.DONT_ASK);
  }

  /**
   * A policy to use to confirm the deletion of additional elements.
   */
  sealed interface ConfirmationPolicy {
    /**
     * Never ask the user; delete additional elements unconditionally
     */
    DontAsk DONT_ASK = new DontAsk();

    /**
     * Never ask the user; delete additional elements unconditionally. Use {@link #DONT_ASK} constant.
     */
    record DontAsk() implements ConfirmationPolicy {
    }

    /**
     * Ask the user if possible
     *
     * @param message         a localized question message to display to the user
     * @param deleteByDefault how to behave if it's impossible to ask the user in current environment.
     *                        If true, the additional elements will be deleted; otherwise they will be kept.
     */
    record Ask(@Nls @NotNull String message, boolean deleteByDefault) implements ConfirmationPolicy {
    }
  }

  /**
   * Detects usages which are not safe to delete.
   *
   * @param  element the element selected for deletion.
   * @param  allElementsToDelete all elements selected for deletion.
   * @return collection of conflict messages that are shown to the user before the deletion is performed.
   *
   * @deprecated Override {@link #findConflicts(PsiElement, PsiElement[], UsageInfo[], MultiMap)} instead to use the new conflicts dialog
   * with preview
   */
  @Deprecated @Nullable
  default Collection<@DialogMessage String> findConflicts(@NotNull PsiElement element, PsiElement @NotNull [] allElementsToDelete) {
    return null;
  }

  /**
   * Detects usages which are not safe to delete. These usages will be shown in a conflict dialog with preview (the new conflicts dialog).
   *
   * @param element  the element selected for deletion.
   * @param allElementsToDelete  all elements that will be deleted.
   * @param usages  usages of the element selected for deletion.
   * @param conflicts  a multimap of conflicting usage elements mapped to one or more messages that will be shown in the conflicts-dialog
   *                              before the deletion is performed.
   */
  default void findConflicts(@NotNull PsiElement element,
                             PsiElement @NotNull [] allElementsToDelete,
                             UsageInfo @NotNull [] usages,
                             @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
  }

  /**
   * Called after the user has confirmed the refactoring. Can filter out some of the usages
   * found by the refactoring. May show UI to ask the user if some of the usages should
   * be excluded.
   *
   * @param project the project where the refactoring happens.
   * @param usages all usages to be processed by the refactoring.
   * @return the filtered list of usages, or null if the user has canceled the refactoring.
   */
  UsageInfo @Nullable [] preprocessUsages(@NotNull Project project, UsageInfo @NotNull [] usages);

  /**
   * Prepares an element for deletion e.g., normalizing declaration so the element declared in the same declaration won't be affected by deletion.
   *
   * Called during {@link BaseRefactoringProcessor#performRefactoring(UsageInfo[])} under write action
   *
   * @param  element an element selected for deletion.
   */
  void prepareForDeletion(@NotNull PsiElement element) throws IncorrectOperationException;

  /**
   * Called to set initial value for "Search in comments" checkbox.
   * @return {@code true} if previous safe delete was executed with "Search in comments" option on.
   */
  boolean isToSearchInComments(PsiElement element);

  /**
   * Called to save chosen for given {@code element} "Search in comments" value.
   */
  void setToSearchInComments(PsiElement element, boolean enabled);

    /**
   * Called to set initial value for "Search for text occurrence" checkbox.
   * @return {@code true} if previous safe delete was executed with "Search for test occurrences" option on.
   */
  boolean isToSearchForTextOccurrences(PsiElement element);

   /**
   * Called to save chosen for given {@code element} "Search for text occurrences" value.
   */
  void setToSearchForTextOccurrences(PsiElement element, boolean enabled);
}
