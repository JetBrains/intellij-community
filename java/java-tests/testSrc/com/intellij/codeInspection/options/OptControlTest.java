// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.codeInspection.options.OptPane.*;
import static org.junit.jupiter.api.Assertions.*;

public class OptControlTest {
  private static class MyInspection extends LocalInspectionTool {
    public int x = 2;
    public double d = 3.0;
    @SuppressWarnings("unused") public boolean y = true;

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        number("x", "X value", 0, 10),
        number("d", "D value", -1, 4),
        checkbox("y", "Y value")
      );
    }
  }

  private static class MyCustomOptionsInspection extends LocalInspectionTool {
    public final Map<String, Boolean> options = new HashMap<>();

    @Override
    public @NotNull OptionController getOptionController() {
      return OptionController.of(
        bindId -> options.getOrDefault(bindId, false),
        (bindId, value) -> options.put(bindId, (Boolean)value)
      );
    }

    @Override
    public @NotNull OptPane getOptionsPane() {
      //noinspection InjectedReferences
      return pane(
        checkbox("check1", "1"),
        checkbox("check2", "2")
      );
    }
  }

  private static class MyDelegateOptionInspection extends LocalInspectionTool {
    public int value;
    public int valueDouble;
    public boolean box;
    public final Map<String, Boolean> options = new HashMap<>();

    @Override
    public @NotNull OptPane getOptionsPane() {
      //noinspection InjectedReferences
      return pane(
        checkbox("box", ""),
        number("value", "", 1, 100),
        number("valueDouble", "", 1, 100),
        group("Advanced",
              checkbox("c1", ""),
              checkbox("c2", "")).prefix("adv")
      );
    }

    @Override
    public @NotNull OptionController getOptionController() {
      return super.getOptionController()
        .onPrefix("adv", bindId -> options.getOrDefault(bindId, true),
                  (bindId, value) -> options.put(bindId, (Boolean)value))
        .onValue("valueDouble", () -> valueDouble / 2, val -> valueDouble = 2 * val);
    }
  }

  @Test
  public void readWrite() {
    MyInspection inspection = new MyInspection();
    OptPane pane = inspection.getOptionsPane();
    assertEquals(2, pane.findControl("x").getValue(inspection));
    assertEquals(3.0, pane.findControl("d").getValue(inspection));
    assertEquals(true, pane.findControl("y").getValue(inspection));
    pane.findControl("x").setValue(inspection, 5);
    assertEquals(5, inspection.x);
    pane.findControl("d").setValue(inspection, 2.5);
    assertEquals(2.5, inspection.d);
  }
  
  @Test
  public void readWriteCustom() {
    MyCustomOptionsInspection inspection = new MyCustomOptionsInspection();
    OptPane pane = inspection.getOptionsPane();
    OptControl check1 = pane.findControl("check1");
    OptControl check2 = pane.findControl("check2");
    assertEquals(Boolean.FALSE, check1.getValue(inspection));
    check1.setValue(inspection, true);
    assertEquals(Boolean.TRUE, check1.getValue(inspection));
    assertEquals(Boolean.FALSE, check2.getValue(inspection));
    check2.setValue(inspection, true);
    assertEquals(Boolean.TRUE, check2.getValue(inspection));
    assertEquals(Map.of("check1", true, "check2", true), inspection.options);
  }

  @Test
  public void readWriteDelegate() {
    MyDelegateOptionInspection inspection = new MyDelegateOptionInspection();
    OptPane pane = inspection.getOptionsPane();
    pane.findControl("box").setValue(inspection, true);
    pane.findControl("value").setValue(inspection, 5);
    pane.findControl("valueDouble").setValue(inspection, 11);
    assertEquals(11, pane.findControl("valueDouble").getValue(inspection));
    pane.findControl("adv.c1").setValue(inspection, true);
    assertEquals(true, pane.findControl("adv.c1").getValue(inspection));
    pane.findControl("adv.c2").setValue(inspection, false);
    assertEquals(5, inspection.value);
    assertEquals(22, inspection.valueDouble);
    assertTrue(inspection.box);
    assertTrue(inspection.options.get("c1"));
    assertFalse(inspection.options.get("c2"));
    assertNull(inspection.options.get("c3"));
  }
}
