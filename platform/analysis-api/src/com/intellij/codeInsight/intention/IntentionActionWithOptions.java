/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Intention action with sub-actions (options)
 */
public interface IntentionActionWithOptions extends IntentionAction {
  @NotNull @Unmodifiable
  List<IntentionAction> getOptions();

  /**
   * If this intention is used as an inspection quick fix, control what should be displayed in the popup.
   * By default, the default inspection submenu is used.
   */
  default @NotNull CombiningPolicy getCombiningPolicy() {
    return CombiningPolicy.InspectionOptionsOnly;
  }

  /**
   * The policy controlling the submenu contents if an inspection returns an {@link IntentionActionWithOptions} as one of its quick fixes
   */
  enum CombiningPolicy {

    /** Show only the default inspection options (Suppress, Fix All, Disable, Open Settings, etc) */
    InspectionOptionsOnly,

    /** Show only the result of {@link com.intellij.codeInsight.intention.IntentionActionWithOptions#getOptions()} */
    IntentionOptionsOnly
  }
}
