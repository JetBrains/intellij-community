
package com.intellij.usageView;

import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;

import javax.swing.*;

public abstract class UsageViewManager {
  public static UsageViewManager getInstance(Project project) {
    return project.getComponent(UsageViewManager.class);
  }

  /**
   * @deprecated
   * @param contentName
   * @param viewDescriptor
   * @param isReusable
   * @param isOpenInNewTab
   * @param isLockable
   * @param progressFactory
   * @return
   */
  public abstract UsageView addContent(String contentName, UsageViewDescriptor viewDescriptor, boolean isReusable, boolean isOpenInNewTab, boolean isLockable, ProgressFactory progressFactory);

  /**
   * @deprecated
   * @param contentName
   * @param viewDescriptor
   * @param isReusable
   * @param isShowReadAccessIcon
   * @param isShowWriteAccessIcon
   * @param isOpenInNewTab
   * @param isLockable
   * @return
   */
  public abstract UsageView addContent(String contentName, UsageViewDescriptor viewDescriptor, boolean isReusable, boolean isShowReadAccessIcon, boolean isShowWriteAccessIcon, boolean isOpenInNewTab, boolean isLockable);

  public abstract Content addContent(String contentName, boolean reusable, final JComponent component, boolean toOpenInNewTab, boolean isLockable);

  public abstract int getReusableContentsCount();

  public abstract Content getSelectedContent(boolean reusable);

  /**
   * @deprecated
   * @return
   */
  public abstract UsageView getSelectedUsageView();

  /**
   * @deprecated
   * @param usageView
   */
  public abstract void closeContent(UsageView usageView);

  public abstract void closeContent(Content usageView);
}