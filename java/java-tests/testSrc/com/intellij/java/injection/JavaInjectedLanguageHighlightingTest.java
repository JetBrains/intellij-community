package com.intellij.java.injection;

import com.intellij.JavaTestUtil;
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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class JavaInjectedLanguageHighlightingTest extends InjectedLanguageTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NonNls private static final String BASE_PATH = "/injection/highlighting";

  private void doTest(boolean checkInfos) {
    doTest(getFilePath(), false, checkInfos);
  }

  private @NotNull String getFilePath() {
    List<File> files = FileUtil.findFilesByMask(Pattern.compile(getTestName(false) + "\\..*"), new File(getTestDataPath() + BASE_PATH));
    File file = assertOneElement(files);
    return StringUtil.trimStart(file.getPath(), getTestDataPath());
  }

  @Override
  @NotNull
  protected List<HighlightInfo> doHighlighting() {
    List<HighlightInfo> infos = new ArrayList<>(super.doHighlighting());
    if (getTestName(false).contains("SyntaxOnly")) {
      boolean injectionFound = removeNotRelevantInfosForSyntaxOnly(infos);
      assertTrue("Injected fragment not found", injectionFound);
    }
    if (getTestName(false).contains("NotEmptyDescription")) {
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

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(new UnusedDeclarationInspection(),
                          new XmlUnresolvedReferenceInspection());
  }

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
    configureFromFileText("x.java", text);

    ExpectedHighlightingData data = new ExpectedHighlightingData(getEditor().getDocument(), false, false);
    data.init();

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    //noinspection ResultOfMethodCallIgnored
    getFile().getText(); //to load text

    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.ERROR);
    assertEmpty(infos);

    //change lang
    WriteCommandAction.runWriteCommandAction(getProject(), ()->getEditor().getDocument().replaceString(getEditor().getDocument().getText().indexOf("TEXT"), getEditor().getDocument().getText().indexOf("TEXT")+"TEXT".length(), "XML"));

    List<HighlightInfo> errors = doHighlighting(HighlightSeverity.ERROR);
    assertSize(2, errors);
    assertEquals("Top level element is not completed", errors.get(0).getDescription());
    assertEquals("Valid XML document must have a root tag", errors.get(1).getDescription());
  }

  public void testTodoHighlightingSyntaxOnlyNotEmptyDescription() {
    doTest(true);
  }

  @Override
  protected boolean doFolding() {
    return getTestName(false).contains("Folding");
  }

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
    configureFromFileText("x.java", text);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();  //update folds later
    bringRealEditorBack();
    assertEmpty(highlightErrors());
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    FoldRegion[] regions = getEditor().getFoldingModel().getAllFoldRegions();

    assertSize(3, regions);
    assertTrue(regions[0].isExpanded());
    assertEquals("/*...*/", regions[0].getPlaceholderText());
    assertEquals(new TextRange(12, 57), ProperTextRange.create(regions[0]));
    assertTrue(regions[1].isExpanded());
    assertEquals("...", regions[1].getPlaceholderText());
    assertEquals(new TextRange(21, 51), ProperTextRange.create(regions[1]));
    assertTrue(regions[2].isExpanded());
    assertEquals("...", regions[2].getPlaceholderText());
    assertEquals(new TextRange(31, 42), ProperTextRange.create(regions[2]));
  }

  public void testEncodedXmlAttributesQuotes() {
    @Language("JAVA")
    String text = """
      class Inj2 {
          String xml = "<?xml version=\\"1.0\\"  encoding=\\"<caret>UTF-8\\"?><x/>";
      }
      """;
    configureFromFileText("x.java", text);
    assertCurrentFileIsInjected();

    bringRealEditorBack();
    assertEmpty(highlightErrors());
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    type('x');
    HighlightInfo info = assertOneElement(highlightErrors());

    assertEquals("Unknown encoding: 'xUTF-8'", info.getDescription());
  }

  public void testXmlHighlightVisitor() {
    doTest(false);
  }

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
    configureFromFileText("x.java", text);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();  //update folds later
    bringRealEditorBack();
    assertEmpty(highlightErrors());
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    int gutterIcons = 0;
    @NotNull Document document = getEditor().getDocument();
    List<LineMarkerInfo<?>> lineMarkers = LineMarkersPass.getDisplayedLineMarkers(document, getProject());
    for (LineMarkerInfo<?> lineMarker : lineMarkers) {
      if (lineMarker.getNavigationHandler() != null) gutterIcons++;
    }
    assertEquals(1, gutterIcons);
  }

  public void testShowErrorDescriptionIsAvailableInsideInjected() {
    @Language("JAVA")
    String fileText = """
      class X {
        @org.intellij.lang.annotations.Language("JAVA")
        private String i="blah<caret>";
      }""";
    configureFromFileText("X.java", fileText);
    assertCurrentFileIsInjected();

    bringRealEditorBack();
    assertInjectedUnderCaret();

    ShowErrorDescriptionAction action = new ShowErrorDescriptionAction();
    AnActionEvent event = TestActionEvent.createTestEvent(action, getCurrentEditorDataContext());
    action.update(event);
    assertTrue(event.getPresentation().isEnabled());
  }

  private void assertCurrentFileIsInjected() {
    assertTrue(getFile().toString(), InjectedLanguageManager.getInstance(getProject()).isInjectedFragment(getFile()));
  }

  private void assertInjectedUnderCaret() {
    doHighlighting();
    assertFalse(getEditor() instanceof EditorWindow);
    assertNotNull(InjectedLanguageUtil.findInjectedPsiNoCommit(getFile(), getEditor().getCaretModel().getOffset()));
  }
}
