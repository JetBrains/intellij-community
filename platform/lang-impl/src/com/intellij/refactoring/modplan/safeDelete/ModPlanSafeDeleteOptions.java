// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.safeDelete;

import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import com.intellij.refactoring.modplan.ModPlanOption;

/**
 * The safe delete options that every language may read.
 */
public final class ModPlanSafeDeleteOptions {
  public static final @NotNull ModPlanOption<Boolean> SEARCH_IN_COMMENTS =
    ModPlanOption.bool("searchInComments", false, RefactoringBundle.messagePointer("search.in.comments.and.strings"));
  public static final @NotNull ModPlanOption<Boolean> SEARCH_IN_NON_CODE =
    ModPlanOption.bool("searchInNonCode", false, RefactoringBundle.messagePointer("search.for.text.occurrences"));
  /**
   * Whether the elements related to a target are searched, such as its base methods. False deletes the targets and what a provider must
   * delete with them.
   */
  public static final @NotNull ModPlanOption<Boolean> SEARCH_RELATED =
    ModPlanOption.bool("searchRelated", true, RefactoringBundle.messagePointer("delete.in.hierarchy"));
  /**
   * Shared by Java and Kotlin: the general answer of the question "delete the parameter in the whole hierarchy?" for every deleted
   * parameter. Only a request sets it; the dialog answers with {@link #HIERARCHY_OF}. An explicit value answers the question of every
   * parameter. The keys of {@link #HIERARCHY_OF} narrow a true.
   */
  public static final @NotNull ModPlanOption<Boolean> DELETE_HIERARCHY =
    ModPlanOption.bool("deleteHierarchy", true, RefactoringBundle.messagePointer("delete.in.hierarchy"));
  /**
   * The parameters that are deleted in the whole hierarchy, one key per line, such as the external name of a Java parameter: a parameter
   * of a list that names only other parameters is deleted alone. The answers of the dialogs add up to this list; a key with the prefix
   * {@code -} was asked about and not chosen, see {@link ModPlanOptionValues#withAnswers}.
   * {@link #DELETE_HIERARCHY} false deletes each parameter alone anyway.
   */
  // TODO IDEA-395594: per-subject answers are kept as text keys among the up-front options of the request; keep them apart, by subject
  @ApiStatus.Internal
  public static final @NotNull ModPlanOption<String> HIERARCHY_OF =
    ModPlanOption.text("hierarchyOf", "", RefactoringBundle.messagePointer("delete.in.hierarchy"));

  private ModPlanSafeDeleteOptions() {
  }
}
