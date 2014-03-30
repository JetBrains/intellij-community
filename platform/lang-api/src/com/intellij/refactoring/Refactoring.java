/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.Ref;
import com.intellij.usageView.UsageInfo;

/**
 * Represents refactoring of IntelliJ IDEA.<br>
 * @author dsl
 */
public interface Refactoring {
  /**
   * Controls whether refactoring shows a preview.<br>
   * If there are modifications that should be performed in non-java files or not in code,
   * preview will be shown regardless of this setting
   */
  void setPreviewUsages(boolean value);

  /**
   * Returns value of <code>previewUsages</code> property.
   * If there are modifications that should be performed in non-java files or not in code,
   * preview will be shown regardless of this setting.
   */
  boolean isPreviewUsages();

  /**
   * Controls whether this refactoring is interactive: whether it should show conflicts dialog
   * and other dialogs. <br>
   * If <code>prepareSuccessfulCallback</code> is not <code>null</code>, refactoring is interactive, and
   * the callback is executed if user did not cancel the refactoring operation, right after
   * <code>preprocessUsages</code> has finished.<br>
   * If <code>prepareSuccessfulCallback</code> is <code>null</code>, user is not presented with
   * conflicts etc., but the preview may still be shown.<br>
   *
   * <b>Note:</b> the callback is executed in dispatch thread. <br>
   *
   * Refactorings are interactive by default, with empty runnable as a callback.
   * @param prepareSuccessfulCallback
   */
  void setInteractive(Runnable prepareSuccessfulCallback);

  /**
   * Returns whether refactoring is interactive.
   * @see #setInteractive(java.lang.Runnable)
   */
  boolean isInteractive();


  /**
   * Searches for places in code that refactoring will change. <br>
   * This method should be best invoked in the process with progress and it does
   * all the lengthy code analysis operations.
   */
  UsageInfo[] findUsages();

  /**
   * Analyses usages (presumably obtained from {@link #findUsages()} for possible conflicts
   * and (if refactoring is interactive) presents user with appropriate conflicts dialog.
   * @see #setInteractive(java.lang.Runnable)
   */
  boolean preprocessUsages(Ref<UsageInfo[]> usages);

  /**
   * Checks whether usages need to be shown
   * @see #setPreviewUsages(boolean)
   * @see #isPreviewUsages()
   */
  boolean shouldPreviewUsages(UsageInfo[] usages);

  /**
   * Performs all changes in code that are neccessary. <br>
   * Does NOT require command or write action, but should be invoked n dispatch thread.
   */
  void doRefactoring(UsageInfo[] usages);

  /**
   * Runs the whole refactoring. The logic of this method is as follows:
   * <p/>
   * <ol>
   * <li> execute {@link #findUsages()} (with progress dialog and all)
   * <li> execute {@link #preprocessUsages(com.intellij.openapi.util.Ref)}
   * <li> if refactoring was not cancelled, check whether we {@link #shouldPreviewUsages(com.intellij.usageView.UsageInfo[])} , and show the preview
   * <li> execute {@link #doRefactoring(com.intellij.usageView.UsageInfo[])} if appropiate (if either the preview
   * was not shown, or 'Do Refactor' button have been pressed)
   * </ol>
   */
  void run();
}
