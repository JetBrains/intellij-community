// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.util;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class EnvVariablesTableTest {

  @Test
  public void parseEnvsFromText() {
    assertEquals(3, EnvVariablesTable.parseEnvsFromText("t1=val1;t2=val2;t3=val3;").size());
    assertEquals(4, EnvVariablesTable.parseEnvsFromText("t1=val1;t2=val2;t3=val3;;empty=;").size());
    assertEquals(3, EnvVariablesTable.parseEnvsFromText("t1=val1;t2=val2;t3=val3;;").size());
    assertEquals(3, EnvVariablesTable.parseEnvsFromText("t1=val1;t2=val2;t3=val3;;;").size());
    assertEquals(3, EnvVariablesTable.parseEnvsFromText("t1=val1;t2=val2;t3=val3;;noise;").size());
    assertEquals(3, EnvVariablesTable.parseEnvsFromText("t1=val1;t2=val2;t3=val3").size());

    Map<String, String> map = EnvVariablesTable.parseEnvsFromText("t1=val1;t2=val2;t3=val\\;3");
    assertEquals(3, map.size());
    assertEquals("val;3", map.get("t3"));

    assertEquals(0, EnvVariablesTable.parseEnvsFromText("test").size());
    assertEquals(1, EnvVariablesTable.parseEnvsFromText("test=test").size());
  }
}