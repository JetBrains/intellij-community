/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.refactoring;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

/**
 * @author ven
 */
public class RefactoringBundle {

  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static Reference<ResourceBundle> ourBundle;
  @NonNls private static final String BUNDLE = "messages.RefactoringBundle";

  private RefactoringBundle() {
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

  public static String getCannotRefactorMessage(@Nullable final String message) {
    return message("cannot.perform.refactoring") + (message == null ? "" : "\n" + message);
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key) {
    return CommonBundle.message(getBundle(), key);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = com.intellij.reference.SoftReference.dereference(ourBundle);
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(BUNDLE);
      ourBundle = new SoftReference<>(bundle);
    }
    return bundle;
  }
}
