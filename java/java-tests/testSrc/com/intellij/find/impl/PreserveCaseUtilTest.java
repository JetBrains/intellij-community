// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import org.junit.Test;

import static com.intellij.find.impl.PreserveCaseUtil.*;
import static org.junit.Assert.*;

/**
 * @author Bas Leijdekkers
 */
public class PreserveCaseUtilTest {

  @Test
  public void testReplaceWithCaseRespect() {
    assertEquals("Foo", replaceWithCaseRespect("foo", "Bar"));
    assertEquals("foo", replaceWithCaseRespect("foo", "bar"));
    assertEquals("FOO", replaceWithCaseRespect("foo", "BAR"));
    assertEquals("Foo", replaceWithCaseRespect("Foo", "Bar"));
    assertEquals("foo", replaceWithCaseRespect("Foo", "bar"));
    assertEquals("FOO", replaceWithCaseRespect("Foo", "BAR"));
    assertEquals("Foo", replaceWithCaseRespect("FOO", "Bar"));
    assertEquals("foo", replaceWithCaseRespect("FOO", "bar"));
    assertEquals("FOO", replaceWithCaseRespect("FOO", "BAR"));
    assertEquals("FooBar", replaceWithCaseRespect("fooBar", "Bar"));
    assertEquals("fooBar", replaceWithCaseRespect("fooBar", "bar"));
    assertEquals("def1", replaceWithCaseRespect("DEF1", "abc1"));
    assertEquals("Def1", replaceWithCaseRespect("DEF1", "Abc1"));
    assertEquals("DEF1", replaceWithCaseRespect("DEF1", "ABC1"));
    assertEquals("abc", replaceWithCaseRespect("abc", "a1"));
    assertEquals("ABC", replaceWithCaseRespect("abc", "A1"));
    assertEquals("report", replaceWithCaseRespect("Report", "display preferences"));
    assertEquals("REPORT", replaceWithCaseRespect("Report", "DISPLAY PREFERENCES"));
    assertEquals("report", replaceWithCaseRespect("Report", "display Preferences"));
    assertEquals("Report", replaceWithCaseRespect("Report", "Display preferences"));
    assertEquals("MYTEST", replaceWithCaseRespect("MyTest", "USERCODE"));
    assertEquals("MyTest", replaceWithCaseRespect("MyTest", "UserCode"));
    assertEquals("myTest", replaceWithCaseRespect("MyTest", "userCode"));
  }

  @Test
  public void testApplyCase() {
    // the same tests as above
    assertEquals("Foo", applyCase("Bar", "foo"));
    assertEquals("foo", applyCase("bar", "foo"));
    assertEquals("FOO", applyCase("BAR", "foo"));
    assertEquals("Foo", applyCase("Bar", "Foo"));
    assertEquals("foo", applyCase("bar", "Foo"));
    assertEquals("FOO", applyCase("BAR", "Foo"));
    assertEquals("Foo", applyCase("Bar", "FOO"));
    assertEquals("foo", applyCase("bar", "FOO"));
    assertEquals("FOO", applyCase("BAR", "FOO"));
    assertEquals("FooBar", applyCase("Bar", "fooBar"));
    assertEquals("fooBar", applyCase("bar", "fooBar"));
    assertEquals("def1", applyCase("abc1", "DEF1"));
    assertEquals("Def1", applyCase("Abc1", "DEF1"));
    assertEquals("DEF1", applyCase("ABC1", "DEF1"));
    assertEquals("abc", applyCase("a1", "abc"));
    assertEquals("ABC", applyCase("A1", "abc"));
    assertEquals("report", applyCase("display preferences", "Report"));
    assertEquals("REPORT", applyCase("DISPLAY PREFERENCES", "Report"));
    assertEquals("report", applyCase("display Preferences", "Report"));
    assertEquals("Report", applyCase("Display preferences", "Report"));
    assertEquals("MYTEST", applyCase("USERCODE", "MyTest"));
    assertEquals("MyTest", applyCase("UserCode", "MyTest"));
    assertEquals("myTest", applyCase("userCode", "MyTest"));

    // cases not supported by replaceWithCaseRespect()
    assertEquals("display preferences", applyCase("report", "display preferences"));
    assertEquals("DISPLAY PREFERENCES", applyCase("REPORT", "display preferences"));
    assertEquals("Display Preferences", applyCase("Report", "display preferences"));
    assertEquals("Program_Lead_Id", applyCase("Project_Lead_Id", "PROGRAM_LEAD_ID"));
    assertEquals("sun_shine", applyCase("niceWeather", "SUN_SHINE"));
    assertEquals("sun_shine", applyCase("niceweather", "SUN_SHINE"));
    assertEquals("do nothing", applyCase("111", "do nothing"));
    assertEquals("Do Nothing", applyCase("111", "Do Nothing"));
    assertEquals("DO NOTHING", applyCase("111", "DO NOTHING"));
    assertEquals("222", applyCase("111", "222"));
    assertEquals("String_Test", applyCase("Test_String", "string_test"));
    assertEquals("_Case", applyCase("Case", "_case"));
    assertEquals("control.searchControl", applyCase("control.search", "control.SearchControl"));
    assertEquals("control.SearchControl", applyCase("control.Search", "control.SearchControl"));
  }

}