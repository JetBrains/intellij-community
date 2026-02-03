// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.java.JavaBundle;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

final class ImportStaticLookupActionProvider implements LookupActionProvider {
  @Override
  public void fillActions(@NotNull LookupElement element,
                          @NotNull Lookup lookup,
                          @NotNull Consumer<? super @NotNull LookupElementAction> consumer) {
    final StaticallyImportable item = element.as(StaticallyImportable.CLASS_CONDITION_KEY);
    if (item == null || !item.canBeImported()) {
      return;
    }

    final Icon checkIcon = PlatformIcons.CHECK_ICON;
    final Icon icon = item.willBeImported() ? checkIcon : EmptyIcon.create(checkIcon);
    consumer.consume(new LookupElementAction(icon, JavaBundle.message("import.statically")) {
      @Override
      public Result performLookupAction() {
        item.setShouldBeImported(!item.willBeImported());
        return new Result.ChooseItem(element);
      }
    });
  }
}
