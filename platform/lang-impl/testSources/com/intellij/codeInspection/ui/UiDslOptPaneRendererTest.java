// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptMultiSelector;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.PlainMessage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;
import static org.junit.jupiter.api.Assertions.*;

@TestApplication
public class UiDslOptPaneRendererTest {
  @TestDisposable
  public Disposable myDisposable;

  private static class MyInspection extends LocalInspectionTool {
    public int myInt = 7;
    public double myDouble = 2.5;
    public int myNegativeInt = -1;
    public String myString = "default 1";
    public boolean myBoolean = false;
    public String myOption = "o1";

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        number("myInt", "before|after", 0, 10),
        number("myDouble", "", 0, 10),
        string("myString", "before|after"),
        dropdown("myOption", "before|after",
                 new OptDropdown.Option("o1", new PlainMessage("option 1")),
                 new OptDropdown.Option("o2", new PlainMessage("option 2"))
        ),
        separator(),
        checkbox("myBoolean", "")
          .description("description"),
        number("myNegativeInt", "", -1000, -1)
      );
    }
  }

  @Test
  public void testControls() {
    MyInspection inspection = new MyInspection();
    JComponent component = render(inspection);

    var textFields = UIUtil.findComponentsOfType(component, JBTextField.class);
    var checkBox = UIUtil.findComponentOfType(component, JCheckBox.class);
    var dropDown = UIUtil.findComponentOfType(component, ComboBox.class);

    assertEquals("7", textFields.get(0).getText());
    assertEquals("2.5", textFields.get(1).getText());
    assertEquals("default 1", textFields.get(2).getText());
    assertEquals("-1", textFields.get(3).getText());
    assertFalse(checkBox.isSelected());
    assertEquals("o1", ((OptDropdown.Option)dropDown.getSelectedItem()).key());
  }

  @Test
  public void testSplitLabel() {
    MyInspection inspection = new MyInspection();
    JComponent component = render(inspection);

    var labels = UIUtil.findComponentsOfType(component, JLabel.class);

    // Number splitLabel
    assertEquals("before", labels.get(0).getText());
    assertEquals("after", labels.get(1).getText());

    // String splitLabel
    assertEquals("before", labels.get(2).getText());
    assertEquals("after", labels.get(3).getText());

    // Dropdown splitLabel
    assertEquals("before", labels.get(4).getText());
    assertEquals("after", labels.get(5).getText());
  }

  @Test
  public void testListeners() {
    MyInspection inspection = new MyInspection();
    JComponent component = render(inspection);

    // Number
    var textFields = UIUtil.findComponentsOfType(component, JBTextField.class);
    textFields.get(0).setText("2");
    assertEquals(2, inspection.myInt);

    textFields.get(1).setText("1.25");
    assertEquals(1.25, inspection.myDouble);

    // String
    textFields.get(2).setText("foo");
    assertEquals("foo", inspection.myString);

    // CheckBox
    var checkBox = UIUtil.findComponentOfType(component, JCheckBox.class);
    checkBox.setSelected(true);
    assertTrue(inspection.myBoolean);

    // Dropdown
    var dropDown = UIUtil.findComponentOfType(component, ComboBox.class);
    dropDown.getModel().setSelectedItem("o2");
    assertEquals("o2", inspection.myOption);
  }

  @Test
  public void testSeparator() {
    MyInspection inspection = new MyInspection();
    JComponent component = render(inspection);
    var separator = UIUtil.findComponentOfType(component, SeparatorComponent.class);
    assertNotNull(separator);
  }

  @Test
  public void testDescription() {
    MyInspection inspection = new MyInspection();
    JComponent component = render(inspection);
    var contextHelp = UIUtil.findComponentOfType(component, ContextHelpLabel.class);
    assertNotNull(contextHelp);
  }

  private static class MyNestedControlsInspection extends LocalInspectionTool {
    public boolean myBoolean = false;
    public boolean myNestedBoolean = true;
    public int myInt1 = 0;
    public int myInt2 = 0;
    public int myInt3 = 0;
    public int myInt4 = 0;

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        checkbox("myBoolean", "", checkbox("myNestedBoolean", "")),
        group("Group Header",
              number("myInt1", "", 0, 0),
              number("myInt2", "", 0, 10),
              number("myInt3", "", 0, 10),
              number("myInt4", "", 0, 10)
        )
      );
    }
  }

  @Test
  public void testNestedControls() {
    MyNestedControlsInspection inspection = new MyNestedControlsInspection();
    JComponent component = render(inspection);

    // Checkbox nested controls
    var checkBoxes = UIUtil.findComponentsOfType(component, JCheckBox.class);
    assertEquals(2, checkBoxes.size());
    assertFalse(checkBoxes.get(1).isEnabled(), "Nested checkbox controls should be disabled if the checkbox is disabled.");

    // Group nested controls
    var integerFields = UIUtil.findComponentsOfType(component, JBTextField.class);
    assertEquals(4, integerFields.size());
  }

  private @NotNull JComponent render(InspectionProfileEntry inspection) {
    return new UiDslOptPaneRenderer().render(inspection.getOptionController(), inspection.getOptionsPane(),
                                             myDisposable, ProjectManager.getInstance().getDefaultProject());
  }

  private static class MyTabsInspection extends LocalInspectionTool {
    public boolean myBoolean = false;
    public int myInt = 0;

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        tabs(
          tab("Tab 1",
              checkbox("myBoolean", "")
          ), tab("Tab 2",
                 number("myInt", "", 0, 10)
          )
        )
      );
    }
  }

  @Test
  public void testTabs() {
    MyTabsInspection inspection = new MyTabsInspection();
    JComponent component = render(inspection);

    // Tabs
    var labels = UIUtil.findComponentsOfType(component, JLabel.class);
    assertTrue(ContainerUtil.exists(labels, label -> "Tab 1".equals(label.getText())));
    assertTrue(ContainerUtil.exists(labels, label -> "Tab 2".equals(label.getText())));

    // Controls inside tab panels
    var checkBoxes = UIUtil.findComponentsOfType(component, JCheckBox.class);
    assertEquals(1, checkBoxes.size());
    var integerFields = UIUtil.findComponentsOfType(component, JBTextField.class);
    assertEquals(1, integerFields.size());
  }

  private static class MyHorizontalStackInspection extends LocalInspectionTool {
    public boolean myBoolean = false;
    public int myInt = 0;

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        horizontalStack(
        checkbox("myBoolean", "Label"),
              number("myInt", "Split|Label", 0, 0)
        )
      );
    }
  }

  @Test
  public void testHorizontalStack() {
    MyHorizontalStackInspection inspection = new MyHorizontalStackInspection();
    JComponent component = render(inspection);

    var checkBoxes = UIUtil.findComponentsOfType(component, JCheckBox.class);
    assertEquals(1, checkBoxes.size());

    var integerFields = UIUtil.findComponentsOfType(component, JBTextField.class);
    assertEquals(1, integerFields.size());

    // Split label support
    var labels = UIUtil.findComponentsOfType(component, JLabel.class);
    assertEquals(2, labels.size());
  }

  private static class MyExpandableFieldInspection extends LocalInspectionTool {
    public String str = "hello,world";

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(expandableString("str", "Enter str", ","));
    }
  }
  
  @Test
  public void testExpandableField() {
    MyExpandableFieldInspection inspection = new MyExpandableFieldInspection();
    JComponent component = render(inspection);
    ExpandableTextField field = UIUtil.findComponentOfType(component, ExpandableTextField.class);
    String text = field.getText();
    assertEquals("hello,world", text);
  }

  private static class StringElement implements OptMultiSelector.OptElement {
    private final String s;

    private StringElement(String s) { this.s = s; }

    @Override
    public @NotNull String getText() { return s; }
  }

  private static class MyMultiSelectorInspection extends LocalInspectionTool {
    public List<StringElement> elements = List.of(new StringElement("String"), new StringElement("Integer"), new StringElement("Double"));
    public List<StringElement> chosen = List.of(elements.get(0));

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        multiSelector("chosen", elements, OptMultiSelector.SelectionMode.SINGLE)
      );
    }
  }

  @Test
  public void testMultiSelectorControl1() {
    MyMultiSelectorInspection inspection = new MyMultiSelectorInspection();
    JComponent component = render(inspection);
    //noinspection unchecked
    JBList<OptMultiSelector.OptElement> list = UIUtil.findComponentOfType(component, JBList.class);
    assertEquals(inspection.chosen, list.getSelectedValuesList());
    ApplicationManager.getApplication().invokeAndWait(() -> {
      list.setSelectedIndex(1);
    });
    assertEquals("Integer", list.getSelectedValue().getText());
    assertEquals("Integer", inspection.chosen.get(0).getText());
    ApplicationManager.getApplication().invokeAndWait(() -> {
      list.setSelectedIndices(new int[]{0, 2});
    });
    assertEquals(1, list.getSelectedValuesList().size());
  }

  private static class MyMultiSelectorInspection2 extends LocalInspectionTool {
    public List<StringElement> elements = List.of(new StringElement("String"), new StringElement("Integer"), new StringElement("Double"));
    public List<StringElement> chosen = List.of();

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        multiSelector("chosen", elements, OptMultiSelector.SelectionMode.MULTIPLE_OR_EMPTY)
      );
    }
  }

  @Test
  public void testMultiSelectorControl2() {
    MyMultiSelectorInspection2 inspection = new MyMultiSelectorInspection2();
    JComponent component = render(inspection);
    //noinspection unchecked
    JBList<OptMultiSelector.OptElement> list = UIUtil.findComponentOfType(component, JBList.class);
    assertEquals(inspection.chosen, list.getSelectedValuesList());
    ApplicationManager.getApplication().invokeAndWait(() -> {
      list.setSelectedIndices(new int[]{0, 2});
    });
    assertEquals("String", list.getSelectedValuesList().get(0).getText());
    assertEquals("Double", list.getSelectedValuesList().get(1).getText());
    assertEquals(inspection.chosen, list.getSelectedValuesList());
  }
}
