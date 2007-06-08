/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;

import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 *         Date: May 2, 2007
 */
public class AntpatternsTest extends TestCase {
  
  public void testPatternConversion() {
    final Pattern testPattern = convertToPattern("**/test/?*.java");
    
    assertTrue(testPattern.matcher("C:/test/AAA.java").matches());
    assertTrue(testPattern.matcher("p1/p2/p3/test/B.java").matches());
    assertTrue(testPattern.matcher("test/AAA.java").matches());
    assertFalse(testPattern.matcher("test/.java").matches());
    assertFalse(testPattern.matcher("tes/AAA.java").matches());
    assertFalse(testPattern.matcher("test/subpackage/AAA.java").matches());
    
    final Pattern sourcesPattern = convertToPattern("**/sources\\");
    assertTrue(sourcesPattern.matcher("C:/sources/HHH.java").matches());
    assertTrue(sourcesPattern.matcher("sources/HHH.class").matches());
    assertTrue(sourcesPattern.matcher("p1/p2/p3/sources/subpackage/TTT.java").matches());
    assertTrue(sourcesPattern.matcher("p1/p2/p3/p4/p5/sources/subpackage/TTT.java").matches());
    assertFalse(sourcesPattern.matcher("p1/source/subpackage/TTT.java").matches());

    final Pattern asteriskPattern = convertToPattern("CVS/**/foo.bar");
    assertFalse(asteriskPattern.matcher("CVS/entries/aaafoo.bar").matches());

    final Pattern asteriskPattern1 = convertToPattern("CVS/**/ttt/");
    assertFalse(asteriskPattern1.matcher("CVS/Attt/foo.bar").matches());
    
    final Pattern cvsPattern = convertToPattern("**/CVS/*");
    assertTrue(cvsPattern.matcher("CVS/Repository").matches());
    assertTrue(cvsPattern.matcher("org/apache/CVS/Entries").matches());
    assertTrue(cvsPattern.matcher("org/apache/jakarta/tools/ant/CVS/Entries").matches());
    assertFalse(cvsPattern.matcher("org/apache/CVS/foo/bar/Entries").matches());
    
    final Pattern jakartaPattern = convertToPattern("org/apache/jakarta/**");
    assertTrue(jakartaPattern.matcher("org/apache/jakarta/tools/ant/docs/index.html").matches());
    assertTrue(jakartaPattern.matcher("org/apache/jakarta/test.xml").matches());
    assertFalse(jakartaPattern.matcher("org/apache/xyz.java").matches());
    
    final Pattern apacheCvsPattern = convertToPattern("org/apache/**/CVS/*");
    assertTrue(apacheCvsPattern.matcher("org/apache/CVS/Entries").matches());
    assertTrue(apacheCvsPattern.matcher("org/apache/jakarta/tools/ant/CVS/Entries").matches());
    assertFalse(apacheCvsPattern.matcher("org/apache/CVS/foo/bar/Entries").matches());

    final Pattern pattern = convertToPattern("/aaa.txt");
    assertFalse(pattern.matcher("/aaa.txt").matches());
    assertTrue(pattern.matcher("aaa.txt").matches());

    final Pattern samplePattern = convertToPattern("dir/subdi*/sample.txt");
    assertTrue(samplePattern.matcher("dir/subdir/sample.txt").matches());

    final Pattern samplePattern2 = convertToPattern("dir/subdi*/");
    assertTrue(samplePattern2.matcher("dir/subdir/sample.txt").matches());
    assertTrue(samplePattern2.matcher("dir/subdir/foo.txt").matches());
    assertTrue(samplePattern2.matcher("dir/subdir/aaa/foo.txt").matches());
  }

  private Pattern convertToPattern(final String antPattern) {
    return Pattern.compile(FileUtil.convertAntToRegexp(antPattern));
  }
}
