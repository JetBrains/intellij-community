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

}