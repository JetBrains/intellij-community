package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 21.01.2003
 * Time: 16:31:32
 * To change this template use Options | File Templates.
 */
public class DotTest extends LightCompletionTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInstance() throws Exception {
    configureByFile("/codeInsight/completion/dot/Dot1.java");

    assertEquals("", myPrefix);
    assertNotNull(myItems);
    int index = 0;
    for (final LookupElement myItem : myItems) {
      if ("a".equals(myItem.getLookupString()) || "foo".equals(myItem.getLookupString())) {
        index++;
      }

    }
    assertEquals(2, index);
  }

  public void testClass() throws Exception {
    configureByFile("/codeInsight/completion/dot/Dot2.java");

    assertEquals("", myPrefix);
    assertNotNull(myItems);
    int index = 0;
    for (final LookupElement myItem : myItems) {
      if ("a".equals(myItem.getLookupString()) || "foo".equals(myItem.getLookupString())) {
        index++;
      }

    }
    assertEquals(2, index);
  }

  public void testAnonymous() throws Exception {
    configureByFile("/codeInsight/completion/dot/Dot3.java");

    assertEquals("", myPrefix);
    assertNotNull(myItems);
    int index = 0;
    for (final LookupElement myItem : myItems) {
      if ("a".equals(myItem.getLookupString()) || "foo".equals(myItem.getLookupString())) {
        index++;
      }

    }
    assertEquals(2, index);
  }

  public void testShowStatic() throws Exception {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean oldSetting = settings.SHOW_STATIC_AFTER_INSTANCE;
    settings.SHOW_STATIC_AFTER_INSTANCE = false;
    configureByFile("/codeInsight/completion/dot/Dot4.java");
    settings.SHOW_STATIC_AFTER_INSTANCE = oldSetting;
    assertEquals("", myPrefix);
    assertNotNull(myItems);
    int index = 0;
    for (final LookupElement myItem : myItems) {
      if ("a".equals(myItem.getLookupString()) || "foo".equals(myItem.getLookupString())) {
        index++;
      }

    }
    assertEquals(1, index);
  }

  public void testImports() throws Exception {
    configureByFile("/codeInsight/completion/dot/Dot5.java");

    assertNotNull(myItems);
    int index = 0;

    for (final LookupElement myItem : myItems) {
      if ("util".equals(myItem.getLookupString()) || "lang".equals(myItem.getLookupString())) {
        index++;
      }

    }
    assertEquals(2, index);
  }

  public void testArrayElement() throws Exception {
    configureByFile("/codeInsight/completion/dot/Dot6.java");

    assertNotNull(myItems);
    int index = 0;

    for (final LookupElement myItem : myItems) {
      if ("toString".equals(myItem.getLookupString()) || "substring".equals(myItem.getLookupString())) {
        index++;
      }
    }
    assertEquals(3, index);
  }

  public void testArray() throws Exception {
    configureByFile("/codeInsight/completion/dot/Dot7.java");

    assertNotNull(myItems);
    int index = 0;

    for (final LookupElement myItem : myItems) {
      if ("length".equals(myItem.getLookupString()) || "clone".equals(myItem.getLookupString())) {
        index++;
      }

    }
    assertEquals(2, index);
  }

  public void testDuplicatesFromInherance() throws Exception {
    configureByFile("/codeInsight/completion/dot/Dot8.java");

    assertNotNull(myItems);
    int index = 0;

    for (final LookupElement myItem : myItems) {
      if ("toString".equals(myItem.getLookupString())) {
        index++;
      }
    }
    assertEquals(1, index);
  }

  public void testConstructorExclusion() throws Exception {
    configureByFile("/codeInsight/completion/dot/Dot9.java");

    assertNotNull(myItems);
    int index = 0;

    for (final LookupElement myItem : myItems) {
      if ("A".equals(myItem.getLookupString())) {
        index++;
      }
    }
    assertEquals(0, index);
  }

  public void testPrimitiveArray() throws Exception {
    configureByFile("/codeInsight/completion/dot/Dot10.java");

    assertNotNull(myItems);
    int index = 0;

    for (final LookupElement myItem : myItems) {
      if ("clone".equals(myItem.getLookupString()) || "length".equals(myItem.getLookupString())) {
        index++;
      }
    }
    assertEquals(2, index);
  }

  public void testThisExpression() throws Exception {
    configureByFile("/codeInsight/completion/dot/Dot11.java");

    assertNotNull(myItems);
    int index = 0;

    for (final LookupElement myItem : myItems) {
      if ("foo1".equals(myItem.getLookupString()) || "foo".equals(myItem.getLookupString())) {
        index++;
      }
    }
    assertEquals(2, index);
  }

  public void testSuperExpression() throws Exception {
    configureByFile("/codeInsight/completion/dot/Dot12.java");

    assertNotNull(myItems);
    int index = 0;

    for (final LookupElement myItem : myItems) {
      if ("foo1".equals(myItem.getLookupString()) || "foo".equals(myItem.getLookupString())) {
        index++;
      }
    }
    assertEquals(1, index);
  }
}
