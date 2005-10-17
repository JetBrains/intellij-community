package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class IJSwingUtilitiesTest extends TestCase {
  private final JPanel myPanel = new JPanel();
  private final Assertion CHECK = new Assertion();

  public void testNoChildren() {
    CHECK.empty(getChildren());
  }

  public void testOneLevel() {
    MockComponent label1 = new MockComponent("1");
    myPanel.add(label1);
    MockComponent label2 = new MockComponent("2");
    myPanel.add(label2);
    CHECK.compareAll(new JComponent[]{label1, label2}, getChildren());
  }

  public void testubTree() {
    MockComponent label1 = new MockComponent("1");
    MockComponent label2 = new MockComponent("2");
    MockComponent label3 = new MockComponent("3");
    MockComponent label4 = new MockComponent("4");
    myPanel.add(label1);
    JPanel subPanel = new JPanel();
    myPanel.add(subPanel);
    subPanel.add(label2);
    subPanel.add(label3);
    myPanel.add(label4);
    CHECK.compareAll(new JComponent[]{label1, subPanel, label2, label3, label4}, getChildren());
  }

  private ArrayList<Component> getChildren() {
    return ContainerUtil.collect(IJSwingUtilities.getChildren(myPanel));
  }

  private static class MockComponent extends JComponent {
    private final String myName;

    public MockComponent(String name) {
      myName = name;
    }

    public String toString() {
      return myName;
    }
  }
}
