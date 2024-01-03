// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;

/**
 * Marker interface for slow {@link SearchEverywhereContributorFactory}.
 * <p>
 * If {@link SearchEverywhereContributorFactory} marked with this interface,
 * methods {@link SearchEverywhereContributorFactory#createContributor(AnActionEvent)} and
 * {@link SearchEverywhereContributorFactory#isAvailable(Project)} will be executed on background thread.
 * <p>
 * It is useful for contributors, which requires some long computation to determine whether it should be created,
 * like the existence of some library in the project.
 * <p>
 * <b>NOTE: FOR NOW FACTORY MUST PROVIDE ONLY CONTRIBUTORS FOR SEPARATE TAB</b>. That means
 * {@link SearchEverywhereContributor#isShownInSeparateTab()} must return {@code true}.
 * And the results of this contributor will be available only in the corresponding tab, but not in the <i>All</i> tab.
 */
@ApiStatus.Experimental
public interface SearchEverywhereContributorSlowFactory {
}
