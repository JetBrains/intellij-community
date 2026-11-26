package com.intellij.java.injection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.LineMarkersPass;
import com.intellij.codeInsight.daemon.impl.actions.ShowErrorDescriptionAction;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.java.injection.InjectedLanguageFixtureKt.injectedLanguageFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@ExtendWith(InjectedLanguageInvariantsExtension.class)
public class JavaInjectedLanguageHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = injectedLanguageFixture(
    projectFixture, () -> List.of(new UnusedDeclarationInspection(), new XmlUnresolvedReferenceInspection()));

  @NonNls private static final String BASE_PATH = "/injection/highlighting";

  @BeforeEach
  void setUp() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    String testName = daemon.getFixture().getTestName(false);
    daemon.setOptions(daemon.getOptions().withHighlightingFilter(infos -> filterInfos(testName, infos)));
    if (testName.contains("Folding")) {
      daemon.setOptions(daemon.getOptions().withFolding());
    }
  }

  private LightJavaCodeInsightTestFixture getFixture() {
    return daemonFixture.get().getFixture();
  }

  private void doTest(boolean checkInfos) {
    daemonFixture.get().doTest(getFilePath(), false, checkInfos);
  }

  private @NotNull String getFilePath() {
    String testDataPath = getFixture().getTestDataPath();
    List<File> files = FileUtil.findFilesByMask(Pattern.compile(getFixture().getTestName(false) + "\\..*"), new File(testDataPath + BASE_PATH));
    assertEquals(1, files.size(), files::toString);
    return BASE_PATH + "/" + files.getFirst().getName();
  }

  private static List<HighlightInfo> filterInfos(String testName, List<HighlightInfo> infos) {
    if (testName.contains("SyntaxOnly")) {
      boolean injectionFound = removeNotRelevantInfosForSyntaxOnly(infos);
      assertTrue(injectionFound, "Injected fragment not found");
    }
    if (testName.contains("NotEmptyDescription")) {
      infos.removeIf(info -> info.getDescription() == null);
    }
    return infos;
  }

  private static boolean removeNotRelevantInfosForSyntaxOnly(@NotNull List<HighlightInfo> infos) {
    boolean injectionFound = false;
    for (int i = infos.size() - 1; i >= 0; i--) {
      HighlightInfo info = infos.get(i);
      if (info.type == HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT) {
        injectionFound = true;
        if (info.getToolTip() != null) {
          infos.remove(i); // whole injected fragment
        }
      }
      else if (info.getSeverity() != HighlightInfoType.SYMBOL_TYPE_SEVERITY || info.getDescription() == null){ //description is not null for todo
        infos.remove(i);
      }
    }
    return injectionFound;
  }

  @Test
  public void testRehighlightAllInjectedOnAnyChange() {
    @Language("JAVA")
    String text = """
      class F {
        class Language {
          {
            //TEXT<caret>
          }
        }

        {
          String lang = "0";
        }
      }""";
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("x.java", text);
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();

    ExpectedHighlightingData data = ReadAction.computeBlocking(() -> new ExpectedHighlightingData(myFixture.getEditor().getDocument(), false, false));
    data.init();

    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments());
    //noinspection ResultOfMethodCallIgnored
    ReadAction.runBlocking(() -> myFixture.getFile().getText()); //to load text

    List<HighlightInfo> infos = daemon.doHighlighting(HighlightSeverity.ERROR);
    assertTrue(infos.isEmpty(), infos::toString);

    //change lang
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), ()->myFixture.getEditor().getDocument().replaceString(myFixture.getEditor().getDocument().getText().indexOf("TEXT"), myFixture.getEditor().getDocument().getText().indexOf("TEXT")+"TEXT".length(), "XML"));

    List<HighlightInfo> errors = daemon.doHighlighting(HighlightSeverity.ERROR);
    assertEquals(2, errors.size(), errors::toString);
    assertEquals("Top level element is not completed", errors.get(0).getDescription());
    assertEquals("Valid XML document must have a root tag", errors.get(1).getDescription());
  }

  @Test
  public void testTodoHighlightingSyntaxOnlyNotEmptyDescription() {
    doTest(true);
  }

  @Test
  public void testFoldingWorksInInjected() {
    @Language("JAVA")
    String text = """
      class Z {
        /*
          <x>
            <y>
            </y>
          </x>
        */
        void xml() {}
      }""";
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("x.java", text);
    EdtTestUtil.runInEdtAndWait(PlatformTestUtil::dispatchAllInvocationEventsInIdeEventQueue);  //update folds later
    daemon.bringRealEditorBack();
    List<HighlightInfo> errors = daemon.highlightErrors();
    assertTrue(errors.isEmpty(), errors::toString);
    EdtTestUtil.runInEdtAndWait(() -> {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      FoldRegion[] regions = daemon.getFixture().getEditor().getFoldingModel().getAllFoldRegions();

      assertEquals(3, regions.length);
      assertTrue(regions[0].isExpanded());
      assertEquals("/*...*/", regions[0].getPlaceholderText());
      assertEquals(new TextRange(12, 57), ProperTextRange.create(regions[0]));
      assertTrue(regions[1].isExpanded());
      assertEquals("...", regions[1].getPlaceholderText());
      assertEquals(new TextRange(21, 51), ProperTextRange.create(regions[1]));
      assertTrue(regions[2].isExpanded());
      assertEquals("...", regions[2].getPlaceholderText());
      assertEquals(new TextRange(31, 42), ProperTextRange.create(regions[2]));
    });
  }

  @Test
  public void testEncodedXmlAttributesQuotes() {
    @Language("JAVA")
    String text = """
      class Inj2 {
          String xml = "<?xml version=\\"1.0\\"  encoding=\\"<caret>UTF-8\\"?><x/>";
      }
      """;
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("x.java", text);
    assertCurrentFileIsInjected();

    daemon.bringRealEditorBack();
    List<HighlightInfo> errors = daemon.highlightErrors();
    assertTrue(errors.isEmpty(), errors::toString);
    EdtTestUtil.runInEdtAndWait(PlatformTestUtil::dispatchAllInvocationEventsInIdeEventQueue);
    daemon.type("x");
    errors = daemon.highlightErrors();
    assertEquals(1, errors.size(), errors::toString);
    HighlightInfo info = errors.getFirst();

    assertEquals("Unknown encoding: 'xUTF-8'", info.getDescription());
  }

  @Test
  public void testXmlHighlightVisitor() {
    doTest(false);
  }

  @Test
  public void testGutterIcons() {
    @Language("JAVA")
    String text = """
      class YYY {
          void f() {
              @org.intellij.lang.annotations.Language("JAVA")
              String text = "class SessionBean implements Runnable {" +
                            "public void run() { }" +
                            "}";
          }
      }
      """;
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("x.java", text);
    EdtTestUtil.runInEdtAndWait(PlatformTestUtil::dispatchAllInvocationEventsInIdeEventQueue);  //update folds later
    daemon.bringRealEditorBack();
    List<HighlightInfo> errors = daemon.highlightErrors();
    assertTrue(errors.isEmpty(), errors::toString);
    EdtTestUtil.runInEdtAndWait(() -> {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      int gutterIcons = 0;
      @NotNull Document document = daemon.getFixture().getEditor().getDocument();
      List<LineMarkerInfo<?>> lineMarkers = LineMarkersPass.getDisplayedLineMarkers(document, daemon.getFixture().getProject());
      for (LineMarkerInfo<?> lineMarker : lineMarkers) {
        if (lineMarker.getNavigationHandler() != null) gutterIcons++;
      }
      assertEquals(1, gutterIcons);
    });
  }

  @Test
  public void testShowErrorDescriptionIsAvailableInsideInjected() {
    @Language("JAVA")
    String fileText = """
      class X {
        @org.intellij.lang.annotations.Language("JAVA")
        private String i="blah<caret>";
      }""";
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("X.java", fileText);
    assertCurrentFileIsInjected();

    daemon.bringRealEditorBack();
    assertInjectedUnderCaret();

    EdtTestUtil.runInEdtAndWait(() -> {
      ShowErrorDescriptionAction action = new ShowErrorDescriptionAction();
      AnActionEvent event = TestActionEvent.createTestEvent(action, daemon.getCurrentEditorDataContext());
      action.update(event);
      assertTrue(event.getPresentation().isEnabled());
    });
  }

  private void assertCurrentFileIsInjected() {
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    ReadAction.runBlocking(() -> assertTrue(InjectedLanguageManager.getInstance(myFixture.getProject()).isInjectedFragment(myFixture.getFile()),
                                            myFixture.getFile().toString()));
  }

  private void assertInjectedUnderCaret() {
    daemonFixture.get().doHighlighting();
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    assertFalse(myFixture.getEditor() instanceof EditorWindow);
    ReadAction.runBlocking(() -> assertNotNull(InjectedLanguageUtil.findInjectedPsiNoCommit(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset())));
  }
}
