/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.rules.ReadAccessFilteringRule;
import com.intellij.usages.impl.rules.WriteAccessFilteringRule;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class UsageFilteringRuleProviderImpl implements UsageFilteringRuleProvider {
  private final ReadWriteState myReadWriteState = new ReadWriteState();

  @NotNull
  public UsageFilteringRule[] getActiveRules(Project project) {
    final List<UsageFilteringRule> rules = new ArrayList<UsageFilteringRule>();
    if (!myReadWriteState.isShowReadAccess()) {
      rules.add(new ReadAccessFilteringRule());
    }
    if (!myReadWriteState.isShowWriteAccess()) {
      rules.add(new WriteAccessFilteringRule());
    }
    return rules.toArray(new UsageFilteringRule[rules.size()]);
  }

  @NotNull
  public AnAction[] createFilteringActions(UsageView view) {
    final UsageViewImpl impl = (UsageViewImpl)view;
    if(view.getPresentation().isCodeUsages()) {
      final JComponent component = view.getComponent();

      final ShowReadAccessUsagesAction showReadAccessUsagesAction = new ShowReadAccessUsagesAction();
      showReadAccessUsagesAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK)), component);

      final ShowWriteAccessUsagesAction showWriteAccessUsagesAction = new ShowWriteAccessUsagesAction();
      showWriteAccessUsagesAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK)), component);

      impl.scheduleDisposeOnClose(new Disposable() {
        public void dispose() {
          showReadAccessUsagesAction.unregisterCustomShortcutSet(component);
          showWriteAccessUsagesAction.unregisterCustomShortcutSet(component);
        }
      });
      return new AnAction[] {showReadAccessUsagesAction, showWriteAccessUsagesAction};
    }
    else {
      return AnAction.EMPTY_ARRAY;
    }
  }

  private static final class ReadWriteState {
    private boolean myShowReadAccess = true;
    private boolean myShowWriteAccess = true;

    public boolean isShowReadAccess() {
      return myShowReadAccess;
    }

    public void setShowReadAccess(final boolean showReadAccess) {
      myShowReadAccess = showReadAccess;
      if (!showReadAccess) {
        myShowWriteAccess = true;
      }
    }

    public boolean isShowWriteAccess() {
      return myShowWriteAccess;
    }

    public void setShowWriteAccess(final boolean showWriteAccess) {
      myShowWriteAccess = showWriteAccess;
      if (!showWriteAccess) {
        myShowReadAccess = true;
      }
    }
  }

  private class ShowReadAccessUsagesAction extends ToggleAction {
    public ShowReadAccessUsagesAction() {
      super(UsageViewBundle.message("action.show.read.access"), null, IconLoader.getIcon("/actions/showReadAccess.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return myReadWriteState.isShowReadAccess();
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myReadWriteState.setShowReadAccess(state);
      Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
      if (project == null) return;
      project.getMessageBus().syncPublisher(RULES_CHANGED).run();
    }
  }

  private class ShowWriteAccessUsagesAction extends ToggleAction {
    public ShowWriteAccessUsagesAction() {
      super(UsageViewBundle.message("action.show.write.access"), null, IconLoader.getIcon("/actions/showWriteAccess.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return myReadWriteState.isShowWriteAccess();
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myReadWriteState.setShowWriteAccess(state);
      Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
      if (project == null) return;
      project.getMessageBus().syncPublisher(RULES_CHANGED).run();
    }
  }
}
