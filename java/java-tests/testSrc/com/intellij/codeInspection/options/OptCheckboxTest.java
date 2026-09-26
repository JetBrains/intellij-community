// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.util.text.HtmlChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class OptCheckboxTest {
  @Test
  public void testDescription() {
    OptCheckbox checkbox = OptPane.checkbox("", "Hello");
    assertNull(checkbox.description());
    checkbox = checkbox.description(HtmlChunk.text("hello"));
    assertEquals("hello", checkbox.description().toString());
  }
  
}
