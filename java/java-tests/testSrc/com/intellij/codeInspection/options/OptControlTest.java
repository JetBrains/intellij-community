// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInspection.options.OptPane.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OptControlTest {
  private static class MyInspection extends LocalInspectionTool {
    public int x = 2;
    public boolean y = true;

    @Override
    public @NotNull OptPane getOptionsPane() {
      return pane(
        number("x", "X value", 0, 10),
        checkbox("y", "Y value")
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
}
