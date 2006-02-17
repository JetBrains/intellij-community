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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.rules.ImportFilteringRule;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jdom.Element;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * @author max
 */
public class UsageFilteringRuleProviderImpl implements UsageFilteringRuleProvider, JDOMExternalizable {
  public boolean SHOW_IMPORTS = true;

  public UsageFilteringRule[] getActiveRules(Project project) {
    if (!SHOW_IMPORTS) {
      return new UsageFilteringRule[] {new ImportFilteringRule()};
    }
    return UsageFilteringRule.EMPTY_ARRAY;
  }

  public AnAction[] createFilteringActions(UsageView view) {
    final UsageViewImpl impl = (UsageViewImpl)view;
    if(view.getPresentation().isCodeUsages()) {
      final JComponent component = view.getComponent();

      final ShowImportsAction showImportsAction = new ShowImportsAction(impl);
      showImportsAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK)), component);

      final ReadWriteState readWriteSharedState = new ReadWriteState();

      final ShowReadAccessUsagesAction showReadAccessUsagesAction = new ShowReadAccessUsagesAction(impl, readWriteSharedState);
      showReadAccessUsagesAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK)), component);

      final ShowWriteAccessUsagesAction showWriteAccessUsagesAction = new ShowWriteAccessUsagesAction(impl, readWriteSharedState);
      showWriteAccessUsagesAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK)), component);

      impl.scheduleDisposeOnClose(new Disposable() {
        public void dispose() {
          showImportsAction.unregisterCustomShortcutSet(component);
          showReadAccessUsagesAction.unregisterCustomShortcutSet(component);
          showWriteAccessUsagesAction.unregisterCustomShortcutSet(component);
        }
      });
      return new AnAction[] {showImportsAction, showReadAccessUsagesAction, showWriteAccessUsagesAction};
    }
    else {
      return AnAction.EMPTY_ARRAY;
    }
  }

  private class ShowImportsAction extends RuleAction {
    public ShowImportsAction(UsageViewImpl view) {
      super(view, UsageViewBundle.message("action.show.import.statements"), IconLoader.getIcon("/actions/showImportStatements.png"));
    }

    protected boolean getOptionValue() {
      return SHOW_IMPORTS;
    }

    protected void setOptionValue(boolean value) {
      SHOW_IMPORTS = value;
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

  private static class ShowReadAccessUsagesAction extends ToggleAction {
    private final UsageViewImpl myView;
    private final ReadWriteState myState;

    public ShowReadAccessUsagesAction(UsageViewImpl view, ReadWriteState state) {
      super(UsageViewBundle.message("action.show.read.access"), null, IconLoader.getIcon("/actions/showReadAccess.png"));
      myView = view;
      myState = state;
    }

    public boolean isSelected(AnActionEvent e) {
      return myState.isShowReadAccess();
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myState.setShowReadAccess(state);
      myView.rulesChanged();
    }
  }

  private static class ShowWriteAccessUsagesAction extends ToggleAction {
    private final UsageViewImpl myView;
    private final ReadWriteState myState;

    public ShowWriteAccessUsagesAction(UsageViewImpl view, ReadWriteState state) {
      super(UsageViewBundle.message("action.show.write.access"), null, IconLoader.getIcon("/actions/showWriteAccess.png"));
      myView = view;
      myState = state;
    }

    public boolean isSelected(AnActionEvent e) {
      return myState.isShowWriteAccess();
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myState.setShowWriteAccess(state);
      myView.rulesChanged();
    }
  }

  public String getComponentName() {
    return "UsageFilteringRuleProvider";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void initComponent() {}
  public void disposeComponent() {}
}
