// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.junit.jupiter.api.Test;

import static com.intellij.codeInspection.options.OptPane.*;
import static org.junit.jupiter.api.Assertions.*;

public class OptPaneTest {
  @Test
  public void construction() {
    assertEquals(EMPTY, pane());
    assertEquals(2, pane(checkbox("one", "One"), checkbox("two", "Two")).components().size());
    assertThrows(IllegalArgumentException.class,
                 () -> 
                   pane(
                     checkbox("one", "One"), 
                     checkbox("two", "Two", 
                              checkbox("one", "Three"))));
  }
  
  @Test
  public void findControl() {
    OptPane pane = pane(
      checkbox("one", "One"),
      checkbox("two", "Two",
               checkbox("three", "Three"),
               number("four", "Four", 0, 10)));
    OptControl four = pane.findControl("four");
    assertTrue(four instanceof OptNumber);
    OptControl three = pane.findControl("three");
    assertTrue(three instanceof OptCheckbox checkbox && checkbox.label().label().equals("Three"));
  }
}
