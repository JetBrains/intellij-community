// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.ide.util.treeView.WeighedItem;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ColoredItem;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.FileColorManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author gregsh
 */
@ApiStatus.Internal
public final class DefaultSearchScopeProviders {
  private DefaultSearchScopeProviders() {}

  public static final class CustomNamed implements SearchScopeProvider {
    @Override
    public String getDisplayName() {
      return LangBundle.message("default.search.scope.custom.named.display.name");
    }

    @Override
    public @NotNull List<SearchScope> getSearchScopes(@NotNull Project project, @NotNull DataContext dataContext) {
      List<SearchScope> result = new ArrayList<>();
      NamedScopesHolder[] holders = NamedScopesHolder.getAllNamedScopeHolders(project);
      for (NamedScopesHolder holder : holders) {
        NamedScope[] scopes = holder.getEditableScopes();  // predefined scopes already included
        for (NamedScope scope : scopes) {
          result.add(wrapNamedScope(project, scope, true));
        }
      }
      return result;
    }
  }

  public static @NotNull GlobalSearchScope wrapNamedScope(@NotNull Project project, @NotNull NamedScope namedScope, boolean colored) {
    GlobalSearchScope scope = GlobalSearchScopesCore.filterScope(project, namedScope);
    if (!colored && !(namedScope instanceof WeighedItem)) return scope;
    int weight = namedScope instanceof WeighedItem ? ((WeighedItem)namedScope).getWeight() : -1;
    Color color = !colored ? null :
                  //namedScope instanceof ColoredItem ? ((ColoredItem)namedScope).getColor() :
                  FileColorManager.getInstance(project).getScopeColor(namedScope.getScopeId());
    return new MyWeightedScope(scope, weight, color);
  }

  private static final class MyWeightedScope extends DelegatingGlobalSearchScope implements WeighedItem, ColoredItem {
    final int weight;
    final Color color;

    MyWeightedScope(@NotNull GlobalSearchScope scope, int weight, Color color) {
      super(scope);
      this.weight = weight;
      this.color = color;
    }

    @Override
    public int getWeight() {
      return weight;
    }

    @Override
    public @Nullable Color getColor() {
      return color;
    }
  }
}
