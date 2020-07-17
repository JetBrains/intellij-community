// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.DynamicBundle;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

public final class RefactoringBundle extends DynamicBundle {
  @NonNls private static final String BUNDLE = "messages.RefactoringBundle";
  private static final RefactoringBundle INSTANCE = new RefactoringBundle();

  private RefactoringBundle() { super(BUNDLE); }

  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
    return INSTANCE.getMessage(key);
  }

  public static String getSearchInCommentsAndStringsText() {
    return message("search.in.comments.and.strings");
  }

  public static String getSearchForTextOccurrencesText() {
    return message("search.for.text.occurrences");
  }

  public static String getVisibilityPackageLocal() {
    return message("visibility.package.local");
  }

  public static String getVisibilityPrivate() {
    return message("visibility.private");
  }

  public static String getVisibilityProtected() {
    return message("visibility.protected");
  }

  public static String getVisibilityPublic() {
    return message("visibility.public");
  }

  public static String getVisibilityAsIs() {
    return message("visibility.as.is");
  }

  public static String getEscalateVisibility() {
    return message("visibility.escalate");
  }

  public static String getCannotRefactorMessage(@NlsContexts.DialogMessage @Nullable final String message) {
    return message("cannot.perform.refactoring") + (message == null ? "" : "\n" + message);
  }
}