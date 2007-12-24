/*
 * @author max
 */
package com.intellij.usages;

import com.intellij.openapi.actionSystem.DataProvider;
import org.jetbrains.annotations.Nullable;

public interface UsageTargetProvider {
  @Nullable
  UsageTarget[] getTargetsAtContext(DataProvider context);
}