/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.SystemInfo;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 10, 2005
 */
@NonNls
public class FileUtilStartsWithTest extends TestCase{
  public void test() {
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr/local/jeka"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr/local/"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr/"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/"));
    assertTrue(FileUtil.startsWith("c:/idea", "c:/"));
    assertTrue(FileUtil.startsWith("c:/idea", "c:"));
    if (SystemInfo.isWindows) {
      assertTrue(FileUtil.startsWith("c:/idea/x", "c:/IDEA"));
    }

    assertFalse(FileUtil.startsWith("/usr/local/jeka", "/usr/local/jek"));
    assertFalse(FileUtil.startsWith("/usr/local/jeka", "/usr/local/aaa"));
    assertFalse(FileUtil.startsWith("/usr/local/jeka", "/usr/local/jeka/"));
    assertFalse(FileUtil.startsWith("/usr/local/jeka", "/aaa"));
    assertFalse(FileUtil.startsWith("c:/idea2", "c:/idea"));
    assertFalse(FileUtil.startsWith("c:/idea_branches/i18n", "c:/idea"));
  }
}
