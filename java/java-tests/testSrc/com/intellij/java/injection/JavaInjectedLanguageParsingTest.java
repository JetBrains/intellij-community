package com.intellij.java.injection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.Place;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.util.List;

import static com.intellij.java.injection.InjectedLanguageFixtureKt.injectedLanguageFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@ExtendWith(InjectedLanguageInvariantsExtension.class)
public class JavaInjectedLanguageParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = injectedLanguageFixture(
    projectFixture, () -> List.of(), LightDaemonAnalyzerOptions.DEFAULT.withBasePath("/java/java-tests/testData/injection/psi/"));

  private void configureByMyFile(@NonNls String ext) {
    daemonFixture.get().configureByFile(daemonFixture.get().getFixture().getTestName(false) + "." + ext);
  }

  @Test
  public void testXmlInLiteral() { doTest("java"); }
  @Test
  public void testInvalidEscapeWithPrefixInJava() { doTest("java"); }

  @Test
  public void testUnescapedOffsetsInJava() {
    configureByMyFile("java");
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.bringRealEditorBack();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    ReadAction.runBlocking(() -> {
      int caretOffset = myFixture.getEditor().getCaretModel().getOffset();
      InjectedLanguageManager manager = InjectedLanguageManager.getInstance(myFixture.getProject());
      PsiElement injectedElementAt = manager.findInjectedElementAt(myFixture.getFile(), caretOffset);
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
        assertTrue(esc.startsWith(unesc), "!\"" + esc + "\".startsWith(\"" + unesc + "\")");
        if (escRaw.length() > 0 && esc.charAt(0) != escRaw.charAt(0)) hostOffset++; // skip |n after |\n
      }
    });
  }

  private void doTest(@NonNls String ext) {
    configureByMyFile(ext);
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    String parseTree = ReadAction.computeBlocking(() -> InjectedLanguageTestUtil.toParseTreeText(myFixture.getFile()));
    PlatformTestUtil.assertSameLinesWithFile(myFixture.getTestDataPath() + File.separatorChar + myFixture.getTestName(false) + ".txt",
                                             parseTree);
  }
}
