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
package com.intellij.usages.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author max
 */
public class UsageNode extends Node implements Comparable<UsageNode>, Navigatable {
  private final Usage myUsage;
  private boolean myUsageExcluded = false;

  public UsageNode(@NotNull Usage usage, @NotNull UsageViewTreeModelBuilder model) {
    super(model);
    setUserObject(usage);
    myUsage = usage;
  }

  public String toString() {
    return myUsage.toString();
  }

  public String tree2string(int indent, String lineSeparator) {
    StringBuffer result = new StringBuffer();
    StringUtil.repeatSymbol(result, ' ', indent);
    result.append(myUsage.toString());
    return result.toString();
  }

  public int compareTo(UsageNode usageNode) {
    return UsageViewImpl.USAGE_COMPARATOR.compare(myUsage, usageNode.getUsage());
  }

  @NotNull
  public Usage getUsage() {
    return myUsage;
  }

  public void navigate(boolean requestFocus) {
    myUsage.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return myUsage.isValid() && myUsage.canNavigate();
  }

  public boolean canNavigateToSource() {
    return myUsage.isValid() && myUsage.canNavigate();
  }

  protected boolean isDataValid() {
    return myUsage.isValid();
  }

  protected boolean isDataReadOnly() {
    return myUsage.isReadOnly();
  }

  protected boolean isDataExcluded() {
    return myUsageExcluded;
  }

  protected String getText(final UsageView view) {
    return Arrays.asList(myUsage.getPresentation().getText()).toString();
  }

  public void setUsageExcluded(boolean usageExcluded) {
    myUsageExcluded = usageExcluded;
  }
}
