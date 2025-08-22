// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util;

import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

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

  @Test // IJPL-200754: Run/Dialog environment variables with semicolon values are broken when pasted in 2025.2
  public void envVarTableRoundtrip() {
    var data = new HashMap<String, String>();
    data.put("var1", "value1");
    data.put("var2", "value2;value2");
    var stringification = EnvironmentVariablesTextFieldWithBrowseButton.stringifyEnvironment(
      EnvironmentVariablesData.create(data, false, null)
    );
    var parsed = EnvVariablesTable.parseEnvsFromText(stringification);
    assertEquals(data, parsed);
  }

  @Test
  public void parseNewFormatEnvsFromText() {
    assertEquals(3, EnvVariablesTable.parseNewEnvsFormatFromText("t1=val1;t2=val2;t3=val3;").size());
    assertEquals(4, EnvVariablesTable.parseNewEnvsFormatFromText("t1=val1;t2=val2;t3=val3;;empty=;").size());
    assertEquals(3, EnvVariablesTable.parseNewEnvsFormatFromText("t1=val1;t2=val2;t3=val3;;").size());
    assertEquals(3, EnvVariablesTable.parseNewEnvsFormatFromText("t1=val1;t2=val2;t3=val3;;;").size());
    assertEquals(4, EnvVariablesTable.parseNewEnvsFormatFromText("t1=val1;t2=val2;t3=val3;;noise;").size());
    assertEquals(3, EnvVariablesTable.parseNewEnvsFormatFromText("t1=val1;t2=val2;t3=val3").size());
    assertEquals("", EnvVariablesTable.parseNewEnvsFormatFromText("key").get("key"));

    var jsonMap = EnvVariablesTable.parseNewEnvsFormatFromText("key=\"value\"blah;");
    assertEquals(1, jsonMap.size());
    assertEquals("valueblah", jsonMap.get("key"));

    jsonMap = EnvVariablesTable.parseNewEnvsFormatFromText("key=\"value\"\"blah\";");
    assertEquals(1, jsonMap.size());
    assertEquals("value\"blah\"", jsonMap.get("key"));

    jsonMap = EnvVariablesTable.parseNewEnvsFormatFromText("key=\"value\\");
    assertEquals(1, jsonMap.size());
    assertEquals("value\\", jsonMap.get("key"));

    jsonMap = EnvVariablesTable.parseNewEnvsFormatFromText("key=\"value;");
    assertEquals(1, jsonMap.size());
    assertEquals("value;", jsonMap.get("key"));

    jsonMap = EnvVariablesTable.parseNewEnvsFormatFromText("key={\"key\" : \"value\"}");
    assertEquals(1, jsonMap.size());
    assertEquals("{\"key\" : \"value\"}", jsonMap.get("key"));

    jsonMap = EnvVariablesTable.parseNewEnvsFormatFromText("key={\"key\" : \"val;ue\"}");
    assertEquals(2, jsonMap.size());
    assertEquals("{\"key\" : \"val", jsonMap.get("key"));
    assertEquals("", jsonMap.get("ue\"}"));

    var map = EnvVariablesTable.parseNewEnvsFormatFromText("t1=val1;t2=val2;t3=val\\;3");
    assertEquals(4, map.size());
    assertEquals("val\\", map.get("t3"));

    assertEquals(1, EnvVariablesTable.parseNewEnvsFormatFromText("test=test").size());

    map = EnvVariablesTable.parseNewEnvsFormatFromText("var1=ffff;var2=C:\\CRM\\files\\;\"va\\\"=;r3\"=aaaa;var4=\"C:\\\\CRM\\\\files\\\\\"");
    assertEquals(4, map.size());
    assertEquals("ffff", map.get("var1"));
    assertEquals("C:\\CRM\\files\\", map.get("var2"));
    assertEquals("C:\\CRM\\files\\", map.get("var4"));
    assertEquals("aaaa", map.get("va\"=;r3"));
  }

  @Test
  public void testEscape() {
    Map<String, String> map3 = EnvVariablesTable.parseNewEnvsFormatFromText("var1=ffff;var2=C:\\CRM\\files\\;\"va\\\"=;r3\"=aaaa;var4=\"C:\\\\CRM\\\\files\\\\\"");
  }
}