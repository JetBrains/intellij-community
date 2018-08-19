// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.NullableNotNullManagerImpl;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jdom.Element;
import org.junit.Assume;

import java.util.List;

public class NullableNotNullManagerTest extends LightPlatformTestCase {
  private NullableNotNullManagerImpl myManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(getProject());
    Assume.assumeTrue(manager instanceof NullableNotNullManagerImpl);
    myManager = (NullableNotNullManagerImpl)manager;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myManager.loadState(new Element("x"));
    }
    finally {
      super.tearDown();
    }
  }

  public void testCannotAddNotNullToNullable() {
    assertNotNull(myManager);
    checkAnnotations();
    myManager.setNotNulls("foo.NotNull");
    checkAnnotations();
    assertTrue(myManager.getNotNulls().contains("foo.NotNull"));
    myManager.setNotNulls(AnnotationUtil.NULLABLE);
    myManager.setNullables(AnnotationUtil.NOT_NULL);
    checkAnnotations();
  }

  private void checkAnnotations() {
    List<String> notNulls = myManager.getNotNulls();
    assertTrue(notNulls.contains(AnnotationUtil.NOT_NULL));
    assertFalse(notNulls.contains(AnnotationUtil.NULLABLE));
    List<String> nullables = myManager.getNullables();
    assertTrue(nullables.contains(AnnotationUtil.NULLABLE));
    assertFalse(nullables.contains(AnnotationUtil.NOT_NULL));
  }
}
