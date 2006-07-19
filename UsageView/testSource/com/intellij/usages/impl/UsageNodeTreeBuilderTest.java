package com.intellij.usages.impl;

import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsagePresentation;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageGroupingRule;
import junit.framework.TestCase;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 5:44:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageNodeTreeBuilderTest extends TestCase {
  protected void setUp() throws Exception {
    super.setUp();
  }

  public void testNoGroupingRules() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{2, 3, 0}, new UsageGroupingRule[] {});

    assertNotNull(groupNode);
    assertNull(groupNode.getParent());

    assertEquals("[2, 3, 0]", groupNode.toString());
  }

  public void testOneGroupingRuleOnly() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{0, 1, 0, 1 , 1}, new UsageGroupingRule[] {new OddEvenGroupingRule()});
    assertEquals("[Even[0, 0], Odd[1, 1, 1]]", groupNode.toString());
  }

  public void testNotGroupedItemsComeToEnd() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{0, 1, 0, 1 , 1, 1003, 1002, 1001}, new UsageGroupingRule[] {new OddEvenGroupingRule()});
    assertEquals("[Even[0, 0], Odd[1, 1, 1], 1003, 1002, 1001]", groupNode.toString());
  }

  public void test2Groupings() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{0, 1, 2, 3, 12, 13, 14, 15, 101, 103, 102, 105, 10001, 10002, 10003}, new UsageGroupingRule[] {
      new OddEvenGroupingRule(),
      new LogGroupingRule()});

    assertEquals("[Even[1[0, 2], 2[12, 14], 3[102]], Odd[1[1, 3], 2[13, 15], 3[101, 103, 105]], 5[10001, 10002, 10003]]", groupNode.toString());
  }

  public void testDifferentRulesDontDependOnOrder() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{10003, 0}, new UsageGroupingRule[] {
      new OddEvenGroupingRule(),
      new LogGroupingRule()});

    assertEquals("[Even[1[0]], 5[10003]]", groupNode.toString());
  }

  public void testGroupsFromDifferentRulesAreCorrectlySorted() throws Exception {
    GroupNode groupNode = buildUsageTree(new int[]{10003, 0, 1, 2, 3, 12, 13, 14, 15, 101, 103, 102, 105, 10001, 10002}, new UsageGroupingRule[] {
      new OddEvenGroupingRule(),
      new LogGroupingRule()});

    assertEquals("[Even[1[0, 2], 2[12, 14], 3[102]], Odd[1[1, 3], 2[13, 15], 3[101, 103, 105]], 5[10003, 10001, 10002]]", groupNode.toString());
  }

  private Usage createUsage(int index) {
    return new MockUsage(index);
  }

  private GroupNode buildUsageTree(int[] indices, UsageGroupingRule[] rules) {
    Usage[] usages = new Usage[indices.length];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = createUsage(indices[i]);
    }

    DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("temp"));
    GroupNode rootNode = new GroupNode(null, 0, model);
    model.setRoot(rootNode);
    UsageNodeTreeBuilder usageNodeTreeBuilder = new UsageNodeTreeBuilder(rules, UsageFilteringRule.EMPTY_ARRAY, rootNode);
    usageNodeTreeBuilder.appendUsages(usages);
    return rootNode;
  }

  private static class LogGroupingRule implements UsageGroupingRule {
    public UsageGroup groupUsage(Usage usage) {
      return new LogUsageGroup(usage.toString().length());
    }
  }

  private static class LogUsageGroup implements UsageGroup {
    private int myPower;

    public LogUsageGroup(int power) {
      myPower = power;
    }

    public void update() {
    }

    public Icon getIcon(boolean isOpen) { return null; }
    public String getText(UsageView view) { return String.valueOf(myPower); }

    public FileStatus getFileStatus() {
      return null;
    }

    public boolean isValid() {
      return false;
    }

    public String toString() {
      return getText(null);
    }

    public int compareTo(UsageGroup o) {
      if (!(o instanceof LogUsageGroup)) return 1;
      return myPower - ((LogUsageGroup)o).myPower;
    }

    public boolean equals(Object o) {
      if (!(o instanceof LogUsageGroup)) return false;
      return myPower == ((LogUsageGroup)o).myPower;
    }
    public int hashCode() { return myPower; }

    public void navigate(boolean requestFocus) { }

    public boolean canNavigate() { return false; }

    public boolean canNavigateToSource() {
      return false;
    }
  }

  private static class OddEvenGroupingRule implements UsageGroupingRule {
    private static final UsageGroup EVEN = new UsageGroup() {
      public Icon getIcon(boolean isOpen) { return null; }
      public String getText(UsageView view) { return "Even"; }

      public void update() {
      }

      public FileStatus getFileStatus() {
        return null;
      }

      public boolean isValid() {
        return false;
      }

      public void navigate(boolean focus) throws UnsupportedOperationException { }
      public boolean canNavigate() { return false; }

      public boolean canNavigateToSource() {
        return false;
      }

      public int compareTo(UsageGroup o) { return o == ODD ? -1 : 0; }
      public String toString() { return getText(null); }
    };

    private static final UsageGroup ODD = new UsageGroup() {
      public Icon getIcon(boolean isOpen) { return null; }
      public String getText(UsageView view) { return "Odd"; }

      public void update() {
      }

      public FileStatus getFileStatus() {
        return null;
      }

      public boolean isValid() {
        return false;
      }

      public void navigate(boolean focus) throws UnsupportedOperationException { }
      public boolean canNavigate() { return false; }

      public boolean canNavigateToSource() {
        return false;
      }

      public int compareTo(UsageGroup o) { return o == EVEN ? 1 : 0; }
      public String toString() { return getText(null); }
    };

    public UsageGroup groupUsage(Usage usage) {
      MockUsage mockUsage = ((MockUsage)usage);

      if (mockUsage.getId() > 1000) return null;

      if (mockUsage.getId() % 2 == 0) {
        return EVEN;
      }
      else {
        return ODD;
      }
    }
  }

  private static class MockUsage implements Usage {
    private int myId;

    public MockUsage(int index) {
      myId = index;
    }


    public int getId() {
      return myId;
    }

    public UsagePresentation getPresentation() {
      return null;
    }

    public boolean isValid() // ?
    {
      return false;
    }

    public FileEditorLocation getLocation() {
      return null;
    }

    public void selectInEditor() {
    }

    public void highlightInEditor() {
    }

    public String toString() {
      return String.valueOf(myId);
    }

    public int compareTo(Usage o) {
      return getId() - ((MockUsage)o).getId();
    }

    public void navigate(boolean requestFocus) {
    }

    public boolean canNavigate() {
      return false;
    }

    public boolean canNavigateToSource() {
      return false;
    }

    public boolean isReadOnly() {
      return false;
    }
  }
}
