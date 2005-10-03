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
import com.intellij.usages.UsageView;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.impl.rules.ImportFilteringRule;
import com.intellij.usages.impl.rules.ReadAccessFilteringRule;
import com.intellij.usages.impl.rules.WriteAccessFilteringRule;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jdom.Element;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 27, 2004
 * Time: 8:20:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageFilteringRuleProviderImpl implements UsageFilteringRuleProvider, JDOMExternalizable {
  public boolean SHOW_IMPORTS = true;
  public boolean SHOW_READ_ACCESS = true;
  public boolean SHOW_WRITE_ACCESS = true;

  public UsageFilteringRule[] getActiveRules(Project project) {
    final List<UsageFilteringRule> rules = new ArrayList<UsageFilteringRule>();
    if (!SHOW_IMPORTS) {
      rules.add(new ImportFilteringRule());
    }
    if (!SHOW_READ_ACCESS) {
      rules.add(new ReadAccessFilteringRule());
    }
    if (!SHOW_WRITE_ACCESS) {
      rules.add(new WriteAccessFilteringRule());
    }
    return rules.toArray(new UsageFilteringRule[rules.size()]);
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

  private final class ReadWriteState {
    private boolean myShowReadAccess = SHOW_READ_ACCESS;
    private boolean myShowWriteAccess = SHOW_WRITE_ACCESS;

    public boolean isShowReadAccess() {
      return myShowReadAccess;
    }

    public void setShowReadAccess(final boolean showReadAccess) {
      myShowReadAccess = showReadAccess;
      if (!showReadAccess) {
        myShowWriteAccess = true;
      }
      flushStateToGlobalSettings();
    }

    public boolean isShowWriteAccess() {
      return myShowWriteAccess;
    }

    public void setShowWriteAccess(final boolean showWriteAccess) {
      myShowWriteAccess = showWriteAccess;
      if (!showWriteAccess) {
        myShowReadAccess = true;
      }
      flushStateToGlobalSettings();
    }

    private void flushStateToGlobalSettings() {
      SHOW_READ_ACCESS = myShowReadAccess;
      SHOW_WRITE_ACCESS = myShowWriteAccess;
    }
  }

  private class ShowReadAccessUsagesAction extends ToggleAction {
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

  private class ShowWriteAccessUsagesAction extends ToggleAction {
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
