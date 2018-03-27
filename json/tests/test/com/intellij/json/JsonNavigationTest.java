package com.intellij.json;

import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.json.psi.JsonProperty;
import com.intellij.psi.PsiElement;

/**
 * @author Mikhail Golubev
 */
public class JsonNavigationTest extends JsonTestCase {

  // WEB-14048
  public void testCopyReference() {
    myFixture.configureByFile("navigation/" + getTestName(false) + ".json");
    final PsiElement element = myFixture.getElementAtCaret();
    assertInstanceOf(element, JsonProperty.class);
    final String qualifiedName = CopyReferenceAction.elementToFqn(element);
    assertEquals("foo.bar[0][0].baz", qualifiedName);
  }
}
