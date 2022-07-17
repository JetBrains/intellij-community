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
 */
@ApiStatus.Internal
public enum GetDataRuleType {
  /**
   * a rule that operates on the data-provider level (default)
   */
  PROVIDER,
  /**
   * same as {@link #PROVIDER} but can also be invoked on the UI thread
   */
  FAST,
  /**
   * a rule that operates on the full data-context level
   */
  CONTEXT
}
