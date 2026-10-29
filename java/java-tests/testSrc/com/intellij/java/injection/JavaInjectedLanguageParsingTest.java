package com.intellij.java.injection;

import com.intellij.JavaTestUtil;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JavaInjectedLanguageParsingTest extends InjectedLanguageTestCase {
  @NotNull
  @Override
  @NonNls
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/injection/psi/";
  }
  private void configureByMyFile(@NonNls String ext) {
    configureByFile(getTestName(false) + "." + ext);
  }

  public void testXmlInLiteral() throws Exception { doTest("java"); }
  public void testInvalidEscapeWithPrefixInJava() throws Exception { doTest("java"); }

  public void testUnescapedOffsetsInJava() throws Exception {
    configureByMyFile("java");
    bringRealEditorBack();
    int caretOffset = getEditor().getCaretModel().getOffset();
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
    PsiElement injectedElementAt = manager.findInjectedElementAt(getFile(), caretOffset);
    assertNotNull(injectedElementAt);
    DocumentWindow documentWindow = InjectedLanguageUtil.getDocumentWindow(injectedElementAt);
    PsiFile injectedFile = injectedElementAt.getContainingFile();
    Place shreds = InjectedLanguageUtil.getShreds(injectedFile);
    String escapedText = injectedFile.getText();
    String unescapedText = manager.getUnescapedText(injectedFile);
    int caretUnescapedOffset = InjectedLanguageUtil.hostToInjectedUnescaped(documentWindow, caretOffset);
    assertTrue(unescapedText.substring(caretUnescapedOffset).startsWith("text"));

    int hostStart = shreds.get(0).getHostRangeMarker().getStartOffset();
    int hostEnd = shreds.get(shreds.size() - 1).getHostRangeMarker().getEndOffset();
    assertTrue(hostStart < hostEnd);
    for (int hostOffset = hostStart; hostOffset <= hostEnd; hostOffset++) {
      int injectedOffset = documentWindow.hostToInjected(hostOffset);
      int unescapedOffset = InjectedLanguageUtil.hostToInjectedUnescaped(documentWindow, hostOffset);
      String escRaw = escapedText.substring(injectedOffset, Math.min(escapedText.length(), injectedOffset + 5));
      String esc = StringUtil.unescapeStringCharacters(escRaw);
      String unesc = unescapedText.substring(unescapedOffset, Math.min(unescapedText.length(), unescapedOffset + 3));
      assertTrue("!\"" + esc + "\".startsWith(\"" + unesc + "\")", esc.startsWith(unesc));
      if (escRaw.length() > 0 && esc.charAt(0) != escRaw.charAt(0)) hostOffset++; // skip |n after |\n
    }
  }

  private void doTest(@NonNls String ext) throws Exception {
    configureByMyFile(ext);
    ParsingTestCase.doCheckResult(getTestDataPath(), getTestName(false) + "." + "txt", toParseTreeText(getFile()));
  }
}
