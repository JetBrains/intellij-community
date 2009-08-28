package com.intellij.navigation;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface GotoClassContributor extends ChooseByNameContributor {
  @Nullable
  String getQualifiedName(NavigationItem item);
}
