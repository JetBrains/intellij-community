package com.intellij.usages.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.usages.impl.rules.*;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleProvider;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 27, 2004
 * Time: 8:20:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageGroupingRuleProviderImpl extends UsageGroupingRuleProvider implements ApplicationComponent, JDOMExternalizable {
  public boolean GROUP_BY_USAGE_TYPE = true;
  public boolean GROUP_BY_MODULE = true;
  public boolean GROUP_BY_PACKAGE = true;
  public boolean GROUP_BY_FILE_STRUCTURE = true;

  public UsageGroupingRule[] getActiveRules(Project project) {
    List<UsageGroupingRule> rules = new ArrayList<UsageGroupingRule>();
    rules.add(new NonCodeUsageGroupingRule());
    if (GROUP_BY_USAGE_TYPE) {
      rules.add(new UsageTypeGroupingRule());
    }
    if (GROUP_BY_MODULE) {
      rules.add(new ModuleGroupingRule());
    }
    if (GROUP_BY_PACKAGE) {
      rules.add(new PackageGroupingRule(project));
    }
    if (GROUP_BY_FILE_STRUCTURE) {
      rules.add(new FileOrClassCompositeGroupingRule(project));
      rules.add(new MethodGroupingRule());
    }
    else {
      rules.add(new FileGroupingRule(project));
    }

    return rules.toArray(new UsageGroupingRule[rules.size()]);
  }

  public AnAction[] createFilteringActions(UsageViewImpl view) {
    return new AnAction[] {
      new GroupByUsageTypeAction(view),
      new GroupByModuleTypeAction(view),
      new GroupByPackageAction(view),
      new GroupByFileStructureAction(view)
    };
  }

  private class GroupByUsageTypeAction extends ToggleAction {
    private UsageViewImpl myView;

    public GroupByUsageTypeAction(UsageViewImpl view) {
      super("Group by usage type", null, IconLoader.getIcon("/ant/filter.png")); //TODO: special icon
      myView = view;
    }

    public boolean isSelected(AnActionEvent e) {
      return GROUP_BY_USAGE_TYPE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      GROUP_BY_USAGE_TYPE = state;
      myView.rulesChanged();
    }
  }

  private class GroupByModuleTypeAction extends ToggleAction {
    private UsageViewImpl myView;

    public GroupByModuleTypeAction(UsageViewImpl view) {
      super("Group by module", null, IconLoader.getIcon("/objectBrowser/showModules.png"));
      myView = view;
    }

    public boolean isSelected(AnActionEvent e) {
      return GROUP_BY_MODULE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      GROUP_BY_MODULE = state;
      myView.rulesChanged();
    }
  }

  private class GroupByPackageAction extends ToggleAction {
    private UsageViewImpl myView;

    public GroupByPackageAction(UsageViewImpl view) {
      super("Group by package", null, IconLoader.getIcon("/toolbar/folders.png"));
      myView = view;
    }

    public boolean isSelected(AnActionEvent e) {
      return GROUP_BY_PACKAGE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      GROUP_BY_PACKAGE = state;
      myView.rulesChanged();
    }
  }

  private class GroupByFileStructureAction extends ToggleAction {
    private UsageViewImpl myView;

    public GroupByFileStructureAction(UsageViewImpl view) {
      super("Group by file structure", null, IconLoader.getIcon("/actions/groupByMethod.png"));
      myView = view;
    }

    public boolean isSelected(AnActionEvent e) {
      return GROUP_BY_FILE_STRUCTURE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      GROUP_BY_FILE_STRUCTURE = state;
      myView.rulesChanged();
    }
  }

  public String getComponentName() {
    return "UsageGroupingRuleProvider";
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
