// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.model.Symbol;
import com.intellij.navigation.SymbolNavigationService;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class NavigatableArrayRule {
  static Navigatable @Nullable [] getData(@NotNull DataMap dataProvider) {
    Navigatable element = dataProvider.get(CommonDataKeys.NAVIGATABLE);
    if (element != null) {
      return new Navigatable[]{element};
    }
    List<Symbol> symbolList = dataProvider.get(CommonDataKeys.SYMBOLS);
    Project project = dataProvider.get(CommonDataKeys.PROJECT);
    if (symbolList != null && project != null) {
      var navigatables = symbolList.stream()
        .flatMap(symbol -> SymbolNavigationService.getInstance().getNavigationTargets(project, symbol).stream())
        .map(target -> SymbolNavigationService.getInstance().getNavigatable(project, target))
        .toArray(Navigatable[]::new);
      if (navigatables.length > 0) {
        return navigatables;
      }
    }
    return null;
  }
}
