package com.intellij.util;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.pom.Navigatable;

public class OpenSourceUtil {

  private OpenSourceUtil() {
  }

  public static void openSourcesFrom(DataContext context, boolean requestFocus) {
    navigate(((Navigatable[])context.getData(DataConstants.NAVIGATABLE_ARRAY)), requestFocus);
  }

  public static void openSourcesFrom(DataProvider context, boolean requestFocus) {
    navigate(((Navigatable[])context.getData(DataConstants.NAVIGATABLE_ARRAY)), requestFocus);
  }

  public static void navigate(final Navigatable[] navigatables, final boolean requestFocus) {
    if (navigatables != null) {
      for (int i = 0; i < navigatables.length; i++) {
        Navigatable navigatable = navigatables[i];
        if (navigatable.canNavigateToSource()) {
          navigatable.navigate(requestFocus);
        }
      }
    }
  }

}
