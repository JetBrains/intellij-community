/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
   * @param usages
   * @return
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
   * <li> execute {@link #preprocessUsages(Ref<UsageInfo[]>)}
   * <li> if refactoring was not cancelled, check whether we {@link #shouldPreviewUsages(UsageInfo[])}, and show the preview
   * <li> execute {@link #doRefactoring(UsageInfo[])} if appropiate (if either the preview
   * was not shown, or 'Do Refactor' button have been pressed)
   * </ol>
   */
  void run();
}
