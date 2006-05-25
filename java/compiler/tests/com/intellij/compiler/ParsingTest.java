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

import java.text.StringCharacterIterator;

public class ParsingTest extends TestCase{

  public void testParseMethodGenericSignature() {
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

  public void testParseClassGenericSignature2() {
    try {
      final BoundsParser parser = new BoundsParser();
      parser.parseClassSignature(new StringCharacterIterator("Lcom/intellij/util/containers/Queue<TT;>.QueueState;"), new StringBuffer());
      assertTrue(true);
    }
    catch (SignatureParsingException e) {
      assertTrue("NOT PARSED:" + e.getMessage(), false);
      e.printStackTrace();
    }
  }

  public void testParseClassGenericSignature3() {
    try {
      final BoundsParser parser = new BoundsParser();
      parser.parseClassSignature(new StringCharacterIterator("Lcom/intellij/util/xml/ui/DomCollectionControl<Lcom/intellij/javaee/model/xml/SecurityRole;>.ControlAddAction;"), new StringBuffer());
      assertTrue(true);
    }
    catch (SignatureParsingException e) {
      assertTrue("NOT PARSED:" + e.getMessage(), false);
      e.printStackTrace();
    }
  }

  public void testParseClassGenericSignature4() {
    try {
      final BoundsParser parser = new BoundsParser();
      parser.parseClassSignature(new StringCharacterIterator("Ljava/lang/Object;Lcom/intellij/util/SpinAllocator<Lcom/intellij/util/io/RandomAccessPagedDataInput;>.IDisposer<Lcom/intellij/util/io/RandomAccessPagedDataInput;>;"), new StringBuffer());
      assertTrue(true);
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
