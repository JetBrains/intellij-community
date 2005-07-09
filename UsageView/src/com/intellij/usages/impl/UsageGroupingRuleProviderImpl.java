package com.intellij.usages.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.Disposable;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.rules.*;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleProvider;
import com.intellij.util.Icons;
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
public class UsageGroupingRuleProviderImpl implements UsageGroupingRuleProvider, JDOMExternalizable {
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
      rules.add(new ClassGroupingRule());
      rules.add(new MethodGroupingRule());
      rules.add(new NonJavaFileGroupingRule(project));
    }
    else {
      rules.add(new FileGroupingRule(project));
    }

    return rules.toArray(new UsageGroupingRule[rules.size()]);
  }

  public AnAction[] createGroupingActions(UsageView view) {
    final UsageViewImpl impl = (UsageViewImpl)view;
    final JComponent component = impl.getComponent();

    final GroupByModuleTypeAction groupByModuleTypeAction = new GroupByModuleTypeAction(impl);
    groupByModuleTypeAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK)), component);

    final GroupByFileStructureAction groupByFileStructureAction = new GroupByFileStructureAction(impl);
    groupByFileStructureAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK)), component);

    impl.scheduleDisposeOnClose(new Disposable() {
      public void dispose() {
        groupByModuleTypeAction.unregisterCustomShortcutSet(component);
        groupByFileStructureAction.unregisterCustomShortcutSet(component);
      }
    });

    if(view.getPresentation().isCodeUsages()) {
      final GroupByUsageTypeAction groupByUsageTypeAction = new GroupByUsageTypeAction(impl);
      groupByUsageTypeAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK)), component);

      final GroupByPackageAction groupByPackageAction = new GroupByPackageAction(impl);
      groupByPackageAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK)), component);

      impl.scheduleDisposeOnClose(new Disposable() {
        public void dispose() {
          groupByUsageTypeAction.unregisterCustomShortcutSet(component);
          groupByPackageAction.unregisterCustomShortcutSet(component);
        }
      });

      return new AnAction[] {
        groupByUsageTypeAction,
        groupByModuleTypeAction,
        groupByPackageAction,
        groupByFileStructureAction
      };
    }
    else {
      return new AnAction[] {
        groupByModuleTypeAction,
        groupByFileStructureAction
      };
    }
  }

  private class GroupByUsageTypeAction extends RuleAction {
    public GroupByUsageTypeAction(UsageViewImpl view) {
      super(view, "Group by usage type", IconLoader.getIcon("/ant/filter.png")); //TODO: special icon
    }
    protected boolean getOptionValue() {
      return GROUP_BY_USAGE_TYPE;
    }
    protected void setOptionValue(boolean value) {
      GROUP_BY_USAGE_TYPE = value;
    }
  }

  private class GroupByModuleTypeAction extends RuleAction {
    public GroupByModuleTypeAction(UsageViewImpl view) {
      super(view, "Group by module", IconLoader.getIcon("/objectBrowser/showModules.png"));
    }

    protected boolean getOptionValue() {
      return GROUP_BY_MODULE;
    }

    protected void setOptionValue(boolean value) {
      GROUP_BY_MODULE = value;
    }
  }

  private class GroupByPackageAction extends RuleAction {
    public GroupByPackageAction(UsageViewImpl view) {
      super(view, "Group by package", Icons.GROUP_BY_PACKAGES);
    }
    protected boolean getOptionValue() {
      return GROUP_BY_PACKAGE;
    }
    protected void setOptionValue(boolean value) {
      GROUP_BY_PACKAGE = value;
    }
  }

  private class GroupByFileStructureAction extends RuleAction {
    public GroupByFileStructureAction(UsageViewImpl view) {
      super(view, "Group by file structure", IconLoader.getIcon("/actions/groupByMethod.png"));
    }
    protected boolean getOptionValue() {
      return GROUP_BY_FILE_STRUCTURE;
    }
    protected void setOptionValue(boolean value) {
      GROUP_BY_FILE_STRUCTURE = value;
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
