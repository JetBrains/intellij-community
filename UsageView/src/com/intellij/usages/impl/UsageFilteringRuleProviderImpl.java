package com.intellij.usages.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.usages.impl.rules.ImportFilteringRule;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jdom.Element;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.awt.event.KeyEvent;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 27, 2004
 * Time: 8:20:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageFilteringRuleProviderImpl extends UsageFilteringRuleProvider implements ApplicationComponent, JDOMExternalizable {
  public boolean FILTER_IMPORTS = false;

  public UsageFilteringRule[] getActiveRules(Project project) {
    final List<UsageFilteringRule> rules = new ArrayList<UsageFilteringRule>();
    if (FILTER_IMPORTS) {
      rules.add(new ImportFilteringRule());
    }
    return rules.toArray(new UsageFilteringRule[rules.size()]);
  }

  public AnAction[] createFilteringActions(UsageViewImpl view) {
    final ShowImportsAction showImportsAction = new ShowImportsAction(view);
    showImportsAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK)), view.getComponent());
    return new AnAction[] {
      showImportsAction,
    };
  }

  private class ShowImportsAction extends ToggleAction {
    private UsageViewImpl myView;

    public ShowImportsAction(UsageViewImpl view) {
      super("Show import statements", null, IconLoader.getIcon("/actions/showImportStatements.png"));
      myView = view;
    }

    public boolean isSelected(AnActionEvent e) {
      return !FILTER_IMPORTS;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      FILTER_IMPORTS = !state;
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
