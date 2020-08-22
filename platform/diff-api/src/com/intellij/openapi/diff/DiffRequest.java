/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.diff;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * A request for a diff or merge operation.
 * @deprecated use {@link com.intellij.diff.requests.DiffRequest} instead
 */
@Deprecated
public abstract class DiffRequest {
  @NonNls private static final String COMMON_DIFF_GROUP_KEY = "DiffWindow";

  private String myGroupKey = COMMON_DIFF_GROUP_KEY;
  @Nullable private final Project myProject;
  private final HashSet myHints = new HashSet();
  private Runnable myOnOkRunnable;

  protected DiffRequest(@Nullable Project project) {
    myProject = project;
  }

  public String getGroupKey() {
    return myGroupKey;
  }

  public void setGroupKey(@NonNls String groupKey) {
    myGroupKey = groupKey;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  /**
   * @return contents to compare
   */
  public abstract DiffContent @NotNull [] getContents();

  /**
   * @return contents names. Should have same length as {@link #getContents()}
   */
  public abstract String[] getContentTitles();

  /**
   * Used as window title
   */
  @NlsContexts.DialogTitle
  public abstract String getWindowTitle();

  /**
   * <B>Work in progress. Don't rely on this functionality</B><br>
   * @return not null (possibly empty) collection of hints for diff tool.
   */
  public Collection getHints() {
    return Collections.unmodifiableCollection(myHints);
  }

  /**
   * @param hint
   * @see DiffRequest#getHints()
   */
  public void addHint(Object hint) {
    myHints.add(hint);
  }

  /**
   * @param hint
   * @see DiffRequest#getHints()
   */
  public void removeHint(Object hint) {
    myHints.remove(hint);
  }

  public Runnable getOnOkRunnable() {
    return myOnOkRunnable;
  }

  public void setOnOkRunnable(Runnable onOkRunnable) {
    myOnOkRunnable = onOkRunnable;
  }
}
