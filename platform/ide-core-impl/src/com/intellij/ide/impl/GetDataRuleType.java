// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import org.jetbrains.annotations.ApiStatus;

/**
 * A type of the rule. Rules can trigger recursive invocation of other rules of the same type.
 * <ul>
 *   <li><b>{@link GetDataRuleType#FAST}</b> - a fast rule that can be invoked on UI thread</li>
 *   <li><b>{@link GetDataRuleType#PROVIDER}</b> - a classic rule invoked on a single level data provider</li>
 *   <li><b>{@link GetDataRuleType#CONTEXT}</b> - a classic rule invoked on the full context data provider</li>
 * </ul>
 *
 * @deprecated Use {@link com.intellij.openapi.actionSystem.DataSink#lazyValue} instead.
 */
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public enum GetDataRuleType {
  /**
   * a rule that operates on the data-provider level (default)
   * <p>
   * ie: if the rule reads multiple data keys, all of them shall be provided by the same {@link com.intellij.openapi.actionSystem.DataProvider}
   * or calculated by other rules based on the data by the same data provider.
   */
  PROVIDER,
  /**
   * a rule that operates on the full data-context level
   */
  CONTEXT,

  /**
   * same as {@link #PROVIDER} but can also be invoked on the UI thread
   * @deprecated Use {@link com.intellij.openapi.actionSystem.UiDataRule} instead.
   */
  @Deprecated(forRemoval = true)
  FAST
}
