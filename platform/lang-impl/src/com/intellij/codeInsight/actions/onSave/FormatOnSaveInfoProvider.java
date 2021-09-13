// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.PlatformUtils.*;

public class FormatOnSaveInfoProvider extends ActionOnSaveInfoProvider {
  @Override
  protected @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull ActionOnSaveContext context) {
    if (isRider()) {
      // Rider has its own 'Format/cleanup/etc. on save' implementation.
      return Collections.emptyList();
    }

    List<ActionOnSaveInfo> result = new ArrayList<>(4);
    result.add(new FormatOnSaveActionInfo(context));

    // If anyone ever decides to rewrite these conditions to become a 'supported IDE list' instead of the current 'unsupported IDE list'
    // please don't forget about Android Studio: it should get all the options that IntelliJ IDEA has.
    if (!isDataGrip() && !isAppCode()) {
      result.add(new OptimizeImportsOnSaveActionInfo(context));

      if (!isPyCharm()) {
        if (Rearranger.EXTENSION.hasAnyExtensions()) {
          result.add(new RearrangeCodeOnSaveActionInfo(context));
        }

        result.add(new CodeCleanupOnSaveActionInfo(context));
      }
    }

    return result;
  }
}
