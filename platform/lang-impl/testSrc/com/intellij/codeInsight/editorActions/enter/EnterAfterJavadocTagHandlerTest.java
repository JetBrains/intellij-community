package com.intellij.codeInsight.editorActions.enter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Denis Zhdanov
 * @since 01/20/2011
 */
public class EnterAfterJavadocTagHandlerTest {

  @Test
  public void textWithoutAsterisk() {
    String text = " <start>";
    EnterAfterJavadocTagHandler.Context context = parse(text);
    assertEmpty(context);
  }
  
  @Test
  public void startTagOnly() {
    String text = "  *   <start>";
    
    // Cursor after single tag
    EnterAfterJavadocTagHandler.Context context = parse(text);
    assertEquals(text.indexOf(">"), context.startTagEndOffset);
    assertTrue(context.endTagStartOffset < 0);
    
    // Cursor before 
    context = parse(text, text.length() - 1);
    assertEmpty(context);
  }
  
  @Test
  public void nestedTags() {
    String text = "  *   <outer><inner> sdf  </inner></outer>";
    
    // Cursor before <outer>.
    EnterAfterJavadocTagHandler.Context context = parse(text, 0);
    assertEmpty(context);
    
    // Cursor at <outer>.
    int offset = text.indexOf("<outer>") + 2;
    context = parse(text, offset);
    assertEmpty(context);
    
    // Cursor between <outer> and <inner>.
    offset = text.indexOf("<inner>");
    context = parse(text, offset);
    assertEquals(offset - 1, context.startTagEndOffset);
    assertEquals(text.indexOf("</outer>"), context.endTagStartOffset);
    
    // Cursor at <inner>.
    offset += 2;
    context = parse(text, offset);
    assertEmpty(context);
    
    // Cursor inside <inner>.
    offset = text.indexOf("sdf");
    context = parse(text, offset);
    assertEquals(text.indexOf("<inner>") + "<inner>".length() - 1, context.startTagEndOffset);
    assertEquals(text.indexOf("</inner>"), context.endTagStartOffset);
    
    // Cursor at </inner>.
    offset = text.indexOf("</inner>") + 2;
    context = parse(text, offset);
    assertEmpty(context);

    // Cursor between </inner> and </outer>.
    offset = text.indexOf("</outer>");
    context = parse(text, offset);
    assertEquals(text.indexOf("<inner>") - 1, context.startTagEndOffset);
    assertEquals(offset, context.endTagStartOffset);
    
    // Cursor at </outer>.
    offset += 2;
    context = parse(text, offset);
    assertEmpty(context);
    
    // Cursor at end.
    context = parse(text);
    assertEmpty(context);
  }
  
  @Test
  public void emptyTag() {
    EnterAfterJavadocTagHandler.Context context = parse("* <p/>");
    assertEmpty(context);
  }

  private static EnterAfterJavadocTagHandler.Context parse(String text) {
    return parse(text, text.length());
  }
  
  private static EnterAfterJavadocTagHandler.Context parse(String text, int offset) {
    return EnterAfterJavadocTagHandler.parse(text, 0, text.length(), offset);
  }
  
  private static void assertEmpty(EnterAfterJavadocTagHandler.Context context) {
    assertTrue(context.startTagEndOffset < 0);
    assertTrue(context.endTagStartOffset < 0);
  }
}
