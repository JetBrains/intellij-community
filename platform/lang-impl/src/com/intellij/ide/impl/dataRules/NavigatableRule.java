// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.model.psi.PsiSymbolService;
import com.intellij.navigation.SymbolNavigationService;
import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT;
import static com.intellij.openapi.actionSystem.CommonDataKeys.PSI_ELEMENT;
import static com.intellij.openapi.actionSystem.CommonDataKeys.SYMBOLS;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEM;

final class NavigatableRule {
  static @Nullable Navigatable getData(@NotNull DataMap dataProvider) {
    var symbols = dataProvider.get(SYMBOLS);
    var project = dataProvider.get(PROJECT);
    if (project != null && symbols != null && !symbols.isEmpty()
        // For PsiElement symbols use logic below
        && (symbols.size() > 1 || PsiSymbolService.getInstance().extractElementFromSymbol(symbols.getFirst()) == null)
    ) {
      var navigatables = symbols.stream()
        .flatMap(symbol -> SymbolNavigationService.getInstance().getNavigationTargets(project, symbol).stream())
        .map(target -> SymbolNavigationService.getInstance().getNavigatable(project, target))
        .toList();
      if (navigatables.size() == 1) {
        return navigatables.getFirst();
      }
    }

    PsiElement element = dataProvider.get(PSI_ELEMENT);
    if (element instanceof Navigatable o) {
      return o;
    }
    if (element != null) {
      return EditSourceUtil.getDescriptor(element);
    }

    Object selection = dataProvider.get(SELECTED_ITEM);
    if (selection instanceof Navigatable o) {
      return o;
    }

    return null;
  }
}
