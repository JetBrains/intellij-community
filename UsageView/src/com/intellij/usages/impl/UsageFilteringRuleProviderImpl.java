package com.intellij.usages.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.usages.impl.rules.ImportFilteringRule;
import com.intellij.usages.impl.rules.ReadAccessFilteringRule;
import com.intellij.usages.impl.rules.WriteAccessFilteringRule;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import com.intellij.util.Icons;
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
public class UsageFilteringRuleProviderImpl extends UsageFilteringRuleProvider implements ApplicationComponent, JDOMExternalizable {
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

  public AnAction[] createFilteringActions(UsageViewImpl view) {
    final ShowImportsAction showImportsAction = new ShowImportsAction(view);
    showImportsAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK)), view.getComponent());

    final ShowReadAccessUsagesAction showReadAccessUsagesAction = new ShowReadAccessUsagesAction(view);
    showReadAccessUsagesAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK)), view.getComponent());

    final ShowWriteAccessUsagesAction showWriteAccessUsagesAction = new ShowWriteAccessUsagesAction(view);
    showWriteAccessUsagesAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK)), view.getComponent());

    return new AnAction[] {showImportsAction, showReadAccessUsagesAction, showWriteAccessUsagesAction};
  }

  private class ShowImportsAction extends ToggleAction {
    private UsageViewImpl myView;

    public ShowImportsAction(UsageViewImpl view) {
      super("Show import statements", null, IconLoader.getIcon("/actions/showImportStatements.png"));
      myView = view;
    }

    public boolean isSelected(AnActionEvent e) {
      return SHOW_IMPORTS;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      SHOW_IMPORTS = state;
      myView.rulesChanged();
    }
  }

  private class ShowReadAccessUsagesAction extends ToggleAction {
    private UsageViewImpl myView;

    public ShowReadAccessUsagesAction(UsageViewImpl view) {
      super("Show read access", null, IconLoader.getIcon("/actions/showReadAccess.png"));
      myView = view;
    }

    public boolean isSelected(AnActionEvent e) {
      return SHOW_READ_ACCESS;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      SHOW_READ_ACCESS = state;
      myView.rulesChanged();
    }
  }

  private class ShowWriteAccessUsagesAction extends ToggleAction {
    private UsageViewImpl myView;

    public ShowWriteAccessUsagesAction(UsageViewImpl view) {
      super("Show write access", null, IconLoader.getIcon("/actions/showWriteAccess.png"));
      myView = view;
    }

    public boolean isSelected(AnActionEvent e) {
      return SHOW_WRITE_ACCESS;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      SHOW_WRITE_ACCESS = state;
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
