/*
 * Copyright 2003-2007 Dave Griffith
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
package com.siyeh.ig.classmetrics;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.NlsContexts;
import com.siyeh.ig.BaseInspection;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public abstract class ClassMetricInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public int m_limit = getDefaultLimit();

  protected abstract int getDefaultLimit();

  protected abstract @NlsContexts.Label String getConfigurationLabel();

  protected int getLimit() {
    return m_limit;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("m_limit", getConfigurationLabel(), 0, Integer.MAX_VALUE)
    );
  }
}