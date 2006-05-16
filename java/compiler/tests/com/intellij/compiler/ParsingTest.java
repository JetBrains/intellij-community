/*
 * Created: Oct 20, 2002 at 8:42:30 PM
 * @author: Jeka
 */
package com.intellij.compiler;

import com.intellij.compiler.classParsing.GenericMethodSignature;
import com.intellij.compiler.classParsing.SignatureParsingException;
import com.intellij.compiler.make.BoundsParser;
import com.intellij.compiler.make.MakeUtil;
import junit.framework.TestCase;

public class ParsingTest extends TestCase{

  public void testParseGenericSignature() {
    try {
      final GenericMethodSignature signature = GenericMethodSignature.parse("<T::Lcom/intellij/util/xml/GenericValue<*>;>(Ljava/util/Collection<TT;>;[Ljava/lang/String;)Ljava/lang/String;");
      assertTrue(signature != null);
    }
    catch (SignatureParsingException e) {
      assertTrue("NOT PARSED:" + e.getMessage(), false);
    }
  }

  public void testParseClassGenericSignature() {
    try {
      final String[] bounds = BoundsParser.getBounds("<Super:Ljava/lang/Object;Sub:Ljava/lang/Object;>Lcom/intellij/ide/DataAccessor<TSub;>;");
      assertTrue(bounds != null);
    }
    catch (SignatureParsingException e) {
      assertTrue("NOT PARSED:" + e.getMessage(), false);
      e.printStackTrace();
    }
  }

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
