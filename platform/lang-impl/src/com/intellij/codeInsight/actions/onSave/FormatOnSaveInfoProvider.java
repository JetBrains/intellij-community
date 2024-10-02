// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider;
import com.intellij.idea.ActionsBundle;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.PlatformUtils.*;

@ApiStatus.Internal
public final class FormatOnSaveInfoProvider extends ActionOnSaveInfoProvider {
  @Override
  protected @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull ActionOnSaveContext context) {
    if (isRider()) {
      // Rider has its own 'Format/cleanup/etc. on save' implementation.
      return Collections.emptyList();
    }

    List<ActionOnSaveInfo> result = new ArrayList<>(4);
    result.add(new FormatOnSaveActionInfo(context));

    if (isOptimizeImportsSupported()) {
      result.add(new OptimizeImportsOnSaveActionInfo(context));
    }
    if (isRearrangeCodeSupported()) {
      result.add(new RearrangeCodeOnSaveActionInfo(context));
    }
    if (isCleanupCodeSupported()) {
      result.add(new CodeCleanupOnSaveActionInfo(context));
    }

    return result;
  }

  private static boolean isOptimizeImportsSupported() {
    return !isDataGrip() && !isAppCode();
  }

  private static boolean isRearrangeCodeSupported() {
    return !isDataGrip() && !isAppCode() && !isPyCharm() && Rearranger.EXTENSION.hasAnyExtensions();
  }

  private static boolean isCleanupCodeSupported() {
    return !isDataGrip() && !isAppCode() && !isPyCharm();
  }

  @Override
  public Collection<String> getSearchableOptions() {
    if (isRider()) {
      // Rider has its own 'Format/cleanup/etc. on save' implementation.
      return Collections.emptyList();
    }

    List<String> result = new ArrayList<>(5);
    result.add(CodeInsightBundle.message("actions.on.save.page.checkbox.reformat.code"));
    result.add(ActionsBundle.message("action.ReformatCode.synonym1"));

    if (isOptimizeImportsSupported()) {
      result.add(CodeInsightBundle.message("actions.on.save.page.checkbox.optimize.imports"));
    }
    if (isRearrangeCodeSupported()) {
      result.add(CodeInsightBundle.message("actions.on.save.page.checkbox.rearrange.code"));
    }
    if (isCleanupCodeSupported()) {
      result.add(CodeInsightBundle.message("actions.on.save.page.checkbox.run.code.cleanup"));
    }

    return result;
  }
}
