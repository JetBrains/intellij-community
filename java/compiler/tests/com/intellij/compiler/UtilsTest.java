/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 10, 2005
 */
public class UtilsTest extends TestCase{
  public void testPathStartsWith() {
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr/local/jeka"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr/local/jek"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr/local/"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr/"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/usr"));
    assertTrue(FileUtil.startsWith("/usr/local/jeka", "/"));

    assertFalse(FileUtil.startsWith("/usr/local/jeka", "/usr/local/aaa"));
    assertFalse(FileUtil.startsWith("/usr/local/jeka", "/usr/local/jeka/"));
    assertFalse(FileUtil.startsWith("/usr/local/jeka", "/aaa"));
  }

  public void testPathStartsWithPerformance() {
    final int repeatCount = 1000000;
    long start = System.currentTimeMillis();
    for (int idx = 0; idx < repeatCount; idx++) {
      startsWithReferenceImpl("/usr/local/jeka", "/ausr/local/jeka");
    }
    System.out.println("Reference implementation took " + (System.currentTimeMillis() - start));

    start = System.currentTimeMillis();
    for (int idx = 0; idx < repeatCount; idx++) {
      FileUtil.startsWith("/usr/local/jeka", "/ausr/local/jeka");
    }
    System.out.println("Optimized implementation took " + (System.currentTimeMillis() - start));
  }

  public static boolean startsWithReferenceImpl(String path1, String path2) {
    if (!SystemInfo.isFileSystemCaseSensitive) {
      path1 = path1.toLowerCase();
      path2 = path2.toLowerCase();
    }
    return path1.startsWith(path2);
  }

}
