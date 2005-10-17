/*
 * Created: Oct 20, 2002 at 8:42:30 PM
 * @author: Jeka
 */
package com.intellij.compiler;

import com.intellij.compiler.make.MakeUtil;
import junit.framework.TestCase;

public class ParsingTest extends TestCase{

  public void testParseObjectType() {
    String parsed = null;

    parsed = MakeUtil.parseObjectType("Ljava/lang/String;", 0);
    assertEquals("Parsed incorrectly ", parsed, "java.lang.String");

    parsed = MakeUtil.parseObjectType("[Ljava/lang/String;", 0);
    assertEquals("Parsed incorrectly ", parsed, "java.lang.String");

    parsed = MakeUtil.parseObjectType("Z", 0);
    assertNull("Parsed incorrectly ", parsed);

  }
}
