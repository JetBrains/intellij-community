// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.codeInspection.options.OptPane.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptControlTest {
  private static class MyInspection extends LocalInspectionTool {
    public int x = 2;
    @SuppressWarnings("unused") public boolean y = true;

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        number("x", "X value", 0, 10),
        checkbox("y", "Y value")
      );
    }
  }

  private static class MyCustomOptionsInspection extends LocalInspectionTool {
    public final Map<String, Boolean> options = new HashMap<>();

    @Override
    public void setOption(@NotNull String bindId, Object value) {
      options.put(bindId, (Boolean)value);
    }

    @Override
    public Object getOption(@NotNull String bindId) {
      return options.getOrDefault(bindId, false);
    }

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        checkbox("check1", "1"),
        checkbox("check2", "2")
      );
    }
  }
  
  
  @Test
  public void readWrite() {
    MyInspection inspection = new MyInspection();
    OptPane pane = inspection.getOptionsPane();
    assertEquals(2, pane.findControl("x").getValue(inspection));
    assertEquals(true, pane.findControl("y").getValue(inspection));
    pane.findControl("x").setValue(inspection, 5);
    assertEquals(5, inspection.x);
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
}
