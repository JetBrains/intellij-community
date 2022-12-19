// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.PlainMessage;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static com.intellij.codeInspection.options.OptPane.*;
import static org.junit.jupiter.api.Assertions.*;

public class UiDslOptPaneRendererTest {
  private static class MyInspection extends LocalInspectionTool {
    public int myInt = 7;
    public String myString = "default 1";
    public boolean myBoolean = false;
    public String myOption = "o1";

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        number("myInt", "before|after", 0, 10),
        string("myString", "before|after"),
        dropdown("myOption", "before|after",
                 new OptDropdown.Option("o1", new PlainMessage("option 1")),
                 new OptDropdown.Option("o2", new PlainMessage("option 2"))
        ),
        separator(),
        checkbox("myBoolean", "")
      );
    }
  }

  @Test
  public void testControls() {
    MyInspection inspection = new MyInspection();
    JComponent component = new UiDslOptPaneRenderer().render(inspection);

    var textFields = UIUtil.findComponentsOfType(component, JBTextField.class);
    var checkBox = UIUtil.findComponentOfType(component, JCheckBox.class);
    var dropDown = UIUtil.findComponentOfType(component, ComboBox.class);

    assertEquals("7", textFields.get(0).getText());
    assertEquals("default 1", textFields.get(1).getText());
    assertFalse(checkBox.isSelected());
    assertEquals("o1", ((OptDropdown.Option)dropDown.getSelectedItem()).key());
  }

  @Test
  public void testSplitLabel() {
    MyInspection inspection = new MyInspection();
    JComponent component = new UiDslOptPaneRenderer().render(inspection);

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
    JComponent component = new UiDslOptPaneRenderer().render(inspection);

    // Number
    var textFields = UIUtil.findComponentsOfType(component, JBTextField.class);
    textFields.get(0).setText("2");
    assertEquals(2, inspection.myInt);

    // String
    textFields.get(1).setText("foo");
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
    JComponent component = new UiDslOptPaneRenderer().render(inspection);
    var separator = UIUtil.findComponentOfType(component, SeparatorComponent.class);
    assertNotNull(separator);
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
              number("myInt1", "", 0, 10),
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
    JComponent component = new UiDslOptPaneRenderer().render(inspection);

    // Checkbox nested controls
    var checkBoxes = UIUtil.findComponentsOfType(component, JCheckBox.class);
    assertEquals(2, checkBoxes.size());
    assertFalse(checkBoxes.get(1).isEnabled(), "Nested checkbox controls should be disabled if the checkbox is disabled.");

    // Group nested controls
    var integerFields = UIUtil.findComponentsOfType(component, IntegerField.class);
    assertEquals(4, integerFields.size());
  }
}
