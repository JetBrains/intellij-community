/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.idea;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ResourceBundle;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 29, 2005
 */
public class ActionsBundle {
  private static Reference<ResourceBundle> ourBundle;

  @NonNls private static final String IDEA_ACTIONS_BUNDLE = "messages.ActionsBundle";

  @SuppressWarnings({"HardCodedStringLiteral", "UnresolvedPropertyKey"})
  public static String actionText(@NonNls String actionId) {
    return message("action." + actionId + ".text");
  }

  @SuppressWarnings({"HardCodedStringLiteral", "UnresolvedPropertyKey"})
  public static String actionDescription(@NonNls String actionId) {
    return message("action." + actionId + ".description");
  }

  public static String message(@PropertyKey(resourceBundle = IDEA_ACTIONS_BUNDLE)String key, Object... params) {
    return CommonBundle.message(getBundle(), key, params);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = null;
    if (ourBundle != null) bundle = ourBundle.get();
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(IDEA_ACTIONS_BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }
}
