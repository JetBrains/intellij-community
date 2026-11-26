package com.intellij.java.injection;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.find.FindManager;
import com.intellij.find.actions.FindUsagesAction;
import com.intellij.find.actions.ShowUsagesAction;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.find.usages.api.SearchTarget;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.java.codeInsight.daemon.impl.DaemonAnnotatorsRespondToChangesTest;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.MarkupModelImpl;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.CommonReferenceProviderTypes;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.testFramework.TestDataProvider;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.util.CommonProcessors;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ipp.concatenation.JoinConcatenatedStringLiteralsIntention;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.java.injection.InjectedLanguageFixtureKt.injectedLanguageFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SkipSlowTestLocally
@TestApplication
@ExtendWith(InjectedLanguageInvariantsExtension.class)
public class JavaInjectedLanguageEditingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = injectedLanguageFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    TestApplicationManager.getInstance().setDataProvider(TestDataProvider.withRules(myFixture.getProject()), myFixture.getTestRootDisposable());
  }

  private LightJavaCodeInsightTestFixture getFixture() {
    return daemonFixture.get().getFixture();
  }

  @Test
  public void testHighlightJavaReferencedFromInjected() {
    daemonFixture.get().configureFromFileText("x.java", """
      package x.y.z; class X { String xml="<x>x.y.z.<caret>X</x>"; }""");
    registerClassReferenceInXmlTokenAndRun(() -> checkUsagesHighlighted(2, "X"));
  }

  private void checkUsagesHighlighted(int num, @NonNls String usage) {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      daemon.ctrlShiftF7();
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      daemon.bringRealEditorBack();
      RangeHighlighter[] highlighters = myFixture.getEditor().getMarkupModel().getAllHighlighters();
      List<RangeHighlighter> l = new ArrayList<>();
      for (RangeHighlighter h : highlighters) {
        boolean unique = true;
        for (RangeHighlighter rangeHighlighter : l) {
          if (TextRange.areSegmentsEqual(h, rangeHighlighter)) {
            unique = false;
            break;
          }
        }
        if (unique) {
          l.add(h);
        }
      }
      assertEquals(num, l.size(), l::toString);
      for (RangeHighlighter highlighter : highlighters) {
        String text = myFixture.getFile().getText().substring(highlighter.getStartOffset(), highlighter.getEndOffset());
        assertEquals(usage, text);
      }
      if (num != 0) {
        daemon.executeAction(IdeActions.ACTION_EDITOR_ESCAPE);
      }
      highlighters = myFixture.getEditor().getMarkupModel().getAllHighlighters();
      assertEquals(0, highlighters.length);
    });
  }

  @Test
  public void testFindUsagesOfJavaReferencedFromInjected() {
    daemonFixture.get().configureFromFileText("x.java", """
      package x.y.z; class X { String xml="<x>x.y.z.<caret>X</x>"; }""");
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    registerClassReferenceInXmlTokenAndRun(() -> ReadAction.runBlocking(() -> {
      FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(myFixture.getProject())).getFindUsagesManager();
      PsiElement element1 = myFixture.getFile();
      PsiClass aClass = ((PsiJavaFile)InjectedLanguageManager.getInstance(element1.getProject()).getTopLevelFile(element1)).getClasses()[0];
      FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(aClass, false);
      assertNotNull(handler);
      List<UsageInfo> infos = new ArrayList<>();
      handler.processElementUsages(aClass, new CommonProcessors.CollectProcessor<>(infos), handler.getFindUsagesOptions());
      assertEquals(1, infos.size(), infos::toString);
      UsageInfo info = infos.getFirst();
      PsiFile file = info.getFile();
      assertEquals(XMLLanguage.INSTANCE, file.getLanguage());
      PsiElement element = info.getElement();
      assertInstanceOf(XmlToken.class, element);
      assertEquals(9, info.getNavigationOffset());
    }));
  }

  @Test
  public void testHighlightAndUnhighlightUsagesInFile() {
    daemonFixture.get().configureFromFileText("x.java", """
      class X { String xml = "<x>java.lang.Runnable</x>";  <caret>Runnable l;  }""");
    checkUsagesHighlighted(2, "Runnable");
    daemonFixture.get().ctrlShiftF7();
    checkUsagesHighlighted(0, null);
  }

  @Test
  public void testHighlightAndUnhighlightUsagesInInjectedFile() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("x.java", """
      class X { String xml = "<x>java.lang.<caret>Runnable</x>";  Runnable l;  }""");
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      daemon.ctrlShiftF7();
      assertEquals(2, myFixture.getEditor().getMarkupModel().getAllHighlighters().length);
      daemon.ctrlShiftF7();
      assertEquals(0, myFixture.getEditor().getMarkupModel().getAllHighlighters().length);
    });
  }

  private void doTestFindUsagesAvailable() {
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      DataContext dataContext = ((EditorEx)myFixture.getEditor()).getDataContext();
      for (String actionId : ContainerUtil.ar(IdeActions.ACTION_FIND_USAGES, ShowUsagesAction.ID)) {
        AnAction action = ActionManager.getInstance().getAction(actionId);
        assertNotNull(action, actionId + " action not found");
        AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContext());
        ActionUtil.updateAction(action, event);
        assertTrue(event.getPresentation().isEnabled(), actionId + "' action is disabled");
      }
      // enabled actions can show "nothing to work on" hint when performed, make sure it's not the case
      DataContext injectedDataContext = AnActionEvent.getInjectedDataContext(dataContext);
      Collection<SearchTarget> targets1 = FindUsagesAction.SEARCH_TARGETS.getData(injectedDataContext);
      UsageTarget[] targets2 = UsageView.USAGE_TARGETS_KEY.getData(injectedDataContext);
      assertFalse((targets1 == null || targets1.isEmpty()) &&
                  (targets2 == null || targets2.length == 0));
    });
  }

  @Test
  public void testFindUsagesAvailable2() {
    daemonFixture.get().configureFromFileText("x.java", """
      class XXX { String java = "public class YYY<caret> {{new YYY();}} "; }""");
    doTestFindUsagesAvailable();
  }

  @Test
  public void testCtrlWWithEscapingAtEnd() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("a.java", """
      class A { String a = "<caret>aaa\\"bbb\\"ccc\\""; }""");
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      daemon.ctrlW();
      assertEquals("aaa", myFixture.getEditor().getSelectionModel().getSelectedText());
      daemon.ctrlW();
      String expected = """
        aaa\\"bbb\\"ccc\\\"""";
      assertEquals(expected, myFixture.getEditor().getSelectionModel().getSelectedText());
    });
  }

  @Test
  public void testFindUsagesDoesNotReportTextOccurrenceInInjectedContext() {
    daemonFixture.get().configureFromFileText("x.java", """
      class XXX { String x = "XXX"; String xml = "XXX"; }""");
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    final PsiClass psiClass = ReadAction.computeBlocking(() -> ((PsiJavaFile)myFixture.getFile()).getClasses()[0]);

    assertTrue(ReadAction.computeBlocking(() -> ReferencesSearch.search(psiClass).findAll()).isEmpty());

    registerClassReferenceInXmlTokenAndRun(() -> {
      assertEquals(1, ReadAction.computeBlocking(() -> ReferencesSearch.search(psiClass).findAll()).size()); //in xml tag injected into "xml"

      // make string literal reference class
      registerClassReferenceInStringLiteralAndRun(() -> {
        assertEquals(2, ReadAction.computeBlocking(() -> ReferencesSearch.search(psiClass).findAll()).size());  // in string literal also
      });
    });
  }

  private void registerClassReferenceInXmlTokenAndRun(@NotNull Runnable run) {
    PsiReferenceRegistrarImpl referenceProvidersRegistry =
      (PsiReferenceRegistrarImpl)ReferenceProvidersRegistry.getInstance().getRegistrar(XMLLanguage.INSTANCE);
    PsiReferenceProvider classReferenceProvider = CommonReferenceProviderTypes.getInstance().getClassReferenceProvider();
    try {
      referenceProvidersRegistry.registerReferenceProvider(PlatformPatterns.psiElement(XmlToken.class), classReferenceProvider);
      dropPsiCaches();
      run.run();
    }
    finally {
      referenceProvidersRegistry.unregisterReferenceProvider(XmlToken.class, classReferenceProvider);
    }
  }
  private void registerClassReferenceInStringLiteralAndRun(Runnable run) {
    PsiReferenceRegistrarImpl referenceProvidersRegistry =
      (PsiReferenceRegistrarImpl)ReferenceProvidersRegistry.getInstance().getRegistrar(JavaLanguage.INSTANCE);
    PsiReferenceProvider classReferenceProvider = CommonReferenceProviderTypes.getInstance().getClassReferenceProvider();
    try {
      referenceProvidersRegistry.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class), classReferenceProvider);
      dropPsiCaches();
      run.run();
    }
    finally {
      referenceProvidersRegistry.unregisterReferenceProvider(PsiLiteralExpression.class, classReferenceProvider);
    }
  }

  private void dropPsiCaches() {
    Project project = getFixture().getProject();
    EdtTestUtil.runInEdtAndWait(() -> PsiManager.getInstance(project).dropPsiCaches());
  }

  @Test
  public void testCommentInInjection() {
    daemonFixture.get().configureFromFileText("X.java", """
      class X { String text="bla<caret>bla"; }""");
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    myFixture.runInCommand(() -> {
      PsiElement element = PsiUtilBase.getElementAtCaret(myFixture.getEditor());
      assertNotNull(element);
      PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)element.getParent();
      boolean result =
        TemporaryPlacesRegistry.getInstance(myFixture.getProject()).getLanguageInjectionSupport().addInjectionInPlace(PlainTextLanguage.INSTANCE, host);
      assertTrue(result);
      PsiElement injected = InjectedLanguageUtil.findElementInInjected(host, 0);
      assertNotNull(injected);
      PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE);
      assertEquals("""
                     //class X { String text="blabla"; }""", myFixture.getEditor().getDocument().getText());
    });
  }

  @Test
  public void testNewlineInPrefixSuffix() {
    final MultiHostInjector injector = new MultiHostInjector() {
      @Override
      public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (context instanceof PsiLiteralExpression) {
          PsiVariable var = PsiTreeUtil.getParentOfType(context, PsiVariable.class);
          if (var == null) return;
          if ("xxx".equals(var.getName())) {
            registrar.startInjecting(PlainTextLanguage.INSTANCE)
              .addPlace("a\nb", "c\nd", (PsiLanguageInjectionHost)context, new TextRange(1, context.getTextLength() - 1))
              .doneInjecting();
          }
        }
      }

      @NotNull
      @Override
      public List<Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(PsiLiteralExpression.class);
      }
    };
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    InjectedLanguageManager.getInstance(myFixture.getProject()).registerMultiHostInjector(injector, myFixture.getTestRootDisposable());
    daemonFixture.get().configureFromFileText("a.java", """
      class X { String xxx = "<caret>vvv";}""");
    Editor editor = myFixture.getEditor();
    assertInstanceOf(EditorWindow.class, editor);
    ReadAction.runBlocking(() -> {
      Document document = editor.getDocument();
      assertEquals(0, document.getLineStartOffset(0));
      assertEquals(2, document.getLineStartOffset(1));
      assertEquals(8, document.getLineStartOffset(2));
      assertEquals(9, document.getLineStartOffset(3));
    });
  }

  @Test
  public void testNullInjectionDoesNotGetCachedForever() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("X.java", """
      class X{ @org.intellij.lang.annotations.Language("XML")
       <caret>String xxx = "<x/>";
      }""");
    daemon.bringRealEditorBack();
    assertInjected(true);
    daemon.type("/");
    assertInjected(false);
    daemon.type("/");
    assertInjected(false);

    daemon.backspace();
    assertInjected(false);
    daemon.backspace();
    assertInjected(true);
  }

  private void assertInjected(boolean mustbe) {
    List<HighlightInfo> infos = daemonFixture.get().doHighlighting();
    HighlightInfo inj = ContainerUtil.find(infos, info -> info.type == HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT);
    assertEquals(mustbe, inj != null, "Injected info: " + infos);
  }

  @Test
  public void testRangeHighlightersOutsideVisibleAreaAreReused() {
    @org.intellij.lang.annotations.Language("JAVA")
    @NonNls String text = "class X{ " +
                  "String[] s = String.valueOf(1).split(\"1,\"); \n"+
                  StringUtil.repeatSymbol('\n',500)+
                  "// xxx<caret>"+
                  StringUtil.repeatSymbol('\n',500)+
                  "String[] s2 = String.valueOf(2).split(\"2,\");\n" +
                  "}";
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("X.java", text);
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();

    EdtTestUtil.runInEdtAndWait(() -> {
      ((EditorImpl)myFixture.getEditor()).getScrollPane().getViewport().setSize(1000, 1000);
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

      myFixture.getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      ((EditorImpl)myFixture.getEditor()).getScrollPane().getViewport().setViewPosition(new Point(0, 1000));
      ((EditorImpl)myFixture.getEditor()).getScrollPane().getViewport().setExtentSize(new Dimension(100, ((EditorImpl)myFixture.getEditor()).getPreferredHeight() - 1000)); // include all till the end
      @NotNull Editor editor = myFixture.getEditor();
      ProperTextRange visibleRange = editor.calculateVisibleRange();
      assertTrue(visibleRange.getStartOffset() > 0);

      assertInjected(true);
      MarkupModel markup = DocumentMarkupModel.forDocument(myFixture.getEditor().getDocument(), myFixture.getProject(), true);
      AtomicInteger addedOutside = new AtomicInteger();
      AtomicInteger deletedOutside = new AtomicInteger();
      ((MarkupModelImpl)markup).addMarkupModelListener(myFixture.getTestRootDisposable(), new MarkupModelListener() {
        @Override
        public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
          if (highlighter.getStartOffset() <= 50 || highlighter.getStartOffset() >= myFixture.getEditor().getDocument().getTextLength() - 50) {
            addedOutside.incrementAndGet();
          }
        }

        @Override
        public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
          if (highlighter.getStartOffset() <= 50 || highlighter.getStartOffset() >= myFixture.getEditor().getDocument().getTextLength() - 50) {
            deletedOutside.incrementAndGet();
          }
        }
      });

      daemon.type("y");
      assertInjected(true);
      assertEquals(0, addedOutside.get());
      assertEquals(0, deletedOutside.get());
    });
  }

  @NotNull
  private static List<DocumentWindow> getAllCachedInjections(PsiFile file) {
    PsiFile topLevelFile = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    return InjectedLanguageManager.getInstance(file.getProject()).getCachedInjectedDocumentsInRange(topLevelFile, topLevelFile.getTextRange());
  }

  @Test
  public void testTypingNearBigHeapOfInjectedFragmentsDoesNotCauseTooManyRangeMarkersAllocated() {
    final MultiHostInjector injector = new MultiHostInjector() {
      @Override
      public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (context instanceof PsiLiteralExpression && ((PsiLiteralExpression)context).getValue() instanceof String) {
          PsiVariable variable = PsiTreeUtil.getParentOfType(context, PsiVariable.class);
          if (variable == null) return;
          if (context.getParent() instanceof PsiPolyadicExpression) return;
          if ("multiInjectHtml".equals(variable.getName())) { // with prefix/suffix
            String text = context.getText();
            for (int o = 0; o<text.length();o++) {
              if (StringUtil.startsWith(text, o, "<inject/>")) {
                registrar.startInjecting(XMLLanguage.INSTANCE);
                registrar.addPlace(null, null, (PsiLanguageInjectionHost)context, new TextRange(o, o+"<inject/>".length()));
                registrar.doneInjecting();
              }
            }
          }
        }
      }

      @Override
      @NotNull
      public List<Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(PsiLiteralExpression.class);
      }
    };
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    InjectedLanguageManager.getInstance(myFixture.getProject()).registerMultiHostInjector(injector, myFixture.getTestRootDisposable());
    int N = 200;
    String text = "class X {\n" +
                  "  String multiInjectHtml = " +
                  "    \"<caret><inject/> " + StringUtil.repeat("<inject/>", N) + "\"; }";
    daemon.configureFromFileText("x.java", text);
    assertInstanceOf(EditorWindow.class, myFixture.getEditor());
    daemon.bringRealEditorBack();
    daemon.doHighlighting();
    EdtTestUtil.runInEdtAndWait(() -> {
      assertEquals(N+1, getAllCachedInjections(myFixture.getFile()).size());
      final Set<SmartPsiElementPointer> pointers = new HashSet<>();
      for (DocumentWindow window : getAllCachedInjections(myFixture.getFile())) {
        PsiFile file = PsiDocumentManager.getInstance(myFixture.getProject()).getPsiFile(window);
        file.accept(new PsiRecursiveElementVisitor() {
          @Override
          public void visitElement(@NotNull PsiElement element) {
            super.visitElement(element);
            if (element instanceof XmlTag) {
              pointers.add(SmartPointerManager.getInstance(myFixture.getProject()).createSmartPsiElementPointer(element));
            }
          }
        });
      }
      final PsiLiteralExpression literalExpression =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PsiLiteralExpression.class);
      for (int i=0; i<10; i++) {
        pointers.forEach(ptr -> assertNotNull(ptr.getElement()));
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        List<DocumentWindow> cached = getAllCachedInjections(myFixture.getFile());
        assertEquals(N+1, cached.size());
        final AtomicInteger literalMarkers = new AtomicInteger();
        final TextRange newLiteralRange = literalExpression.getTextRange().grown(1); // type space
        daemon.type(" ");
        ((DocumentImpl)myFixture.getEditor().getDocument()).processRangeMarkersOverlappingWith(newLiteralRange.getStartOffset(),
                                                                                     newLiteralRange.getEndOffset(),
                                                                                     marker -> {
                                                                                       if (newLiteralRange.equalsToRange(marker.getStartOffset(), marker.getEndOffset())) {
                                                                                         literalMarkers.incrementAndGet();
                                                                                       }
                                                                                       return true;
                                                                                     });

        PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments(); //todo remove and see exceptions
        daemon.doHighlighting();                                                  //todo remove and see exceptions

        Editor editor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myFixture.getEditor(), myFixture.getFile());
        assertInstanceOf(EditorWindow.class, editor);

        assertTrue(literalMarkers.get() < N, String.valueOf(literalMarkers));
      }
    });
  }

  @Test
  public void testCommitAfterReplaceTextAfterJoinLiteralsDoesntCrash() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureByFile("/injection/javaConcat.java");
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    daemon.invoke(new JoinConcatenatedStringLiteralsIntention().asIntention());

    PsiFile file = myFixture.getFile();
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> myFixture.getDocument(file).setText(myFixture.getDocument(file).getText()));

    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments());
    daemon.bringRealEditorBack();
    assertInjected(true);
  }

  @Test
  public void testSuppressWithNonNlsCommentCrashesOnInjectionWithSuffix() {
    @org.intellij.lang.annotations.Language("JAVA")
    String text = """
      class s {\
            void g(@org.intellij.lang.annotations.Language(value = "JAVA", prefix = "class Dummy {", suffix = "}") String s1, String s) {}\
              {g("void x(<caret>String s) {}",
                      "ddd");}\
      }""";
    daemonFixture.get().configureFromFileText("test.java", text);
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      I18nInspection inspection = new I18nInspection();
      SuppressIntentionAction action = inspection.getSuppressActions(null)[0];
      assertEquals("Suppress with 'NON-NLS' comment", action.getText());
      assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), ()->action.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
    });

    myFixture.checkResult("""
                        class s {\
                              void g(@org.intellij.lang.annotations.Language(value = "JAVA", prefix = "class Dummy {", suffix = "}") String s1, String s) {}\
                                {g("void x(<caret>String s) {}", //NON-NLS
                                        "ddd");}\
                        }""");
  }

  @Test
  public void testTyping() {
    @org.intellij.lang.annotations.Language("XML")
    String xml = "<input type='text'/>";
    @org.intellij.lang.annotations.Language("JAVA")
    String fileText = "class XXX { \n" +
                      "  void f() {\n" +
                      "    String xml = \"<caret>" + xml + "\";\n" +
                      "  }\n" +
                      "}";
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("x.java", fileText);
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    assertInjected(true);
    assertEquals(xml, ReadAction.computeBlocking(() -> myFixture.getFile().getText()));
    List<HighlightInfo> errors = daemon.highlightErrors();
    assertTrue(errors.isEmpty(), errors::toString);
    daemon.type("  ");
    errors = daemon.highlightErrors();
    assertTrue(errors.isEmpty(), errors::toString);
    assertEquals("  "+xml, ReadAction.computeBlocking(() -> myFixture.getFile().getText()));
  }

  @Test
  public void testSmartBackspaceWithInjectionPrefix() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    InjectedLanguageManager.getInstance(myFixture.getProject()).registerMultiHostInjector(new MultiHostInjector() {
      @Override
      public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        registrar.startInjecting(JavaLanguage.INSTANCE)
                 .addPlace("class Foo {\n", "\n}", (PsiLanguageInjectionHost)context, new TextRange(1, context.getTextLength() - 1))
                 .doneInjecting();
      }

      @NotNull
      @Override
      public List<Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(PsiLiteralExpressionImpl.class);
      }
    }, myFixture.getTestRootDisposable());
    daemon.configureFromFileText("test.java", """
      class C { String javaPr = " <caret>int a = 1;"; }""");
    daemon.bringRealEditorBack(); // backspace action is invoked on host editor, it finds injected editor itself
    daemon.backspace();
    myFixture.checkResult("""
                        class C { String javaPr = "<caret>int a = 1;"; }""");
  }

  @Test
  public void testCachedValuesDependentOnInjectedFilesMustBeInvalidatedOnPsiChangeInNonPhysicalHost() {
    @org.intellij.lang.annotations.Language("JAVA")
    String fileText = """
      class XXX {\s
        void f() {
          String xml = "b";
        }
      }""";

    Project project = getFixture().getProject();
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiFile hostFile = (PsiFile)PsiFileFactory.getInstance(project).createFileFromText("a.java", JavaLanguage.INSTANCE, fileText).copy();
      int offset = fileText.indexOf("b\"");
      PsiFile injectedFile = InjectedLanguageUtilBase.findInjectedPsiNoCommit(hostFile, offset);
      assertNotNull(injectedFile);


      CachedValue<String> cv = CachedValuesManager.getManager(project)
        .createCachedValue(() -> CachedValueProvider.Result.create(injectedFile.getText(), injectedFile));

      assertEquals("b", cv.getValue());

      ((LeafElement)hostFile.findElementAt(offset)).replaceWithText("\"ab\"");

      assertSame(injectedFile, InjectedLanguageUtilBase.findInjectedPsiNoCommit(hostFile, offset));
      assertTrue(injectedFile.isValid());
      assertEquals("ab", injectedFile.getText());
      assertEquals("ab", cv.getValue());
    });
  }

  @Test
  public void testMoveElementLeftAction() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("test.java", """
      class C { String java = "class I { int a = 1 + 2<caret>; }"; }""");
    daemon.bringRealEditorBack();
    daemon.executeAction(IdeActions.MOVE_ELEMENT_LEFT);
    daemon.getFixture().checkResult("""
                        class C { String java = "class I { int a = 2<caret> + 1; }"; }""");
  }

  @Test
  public void testCacheNegativeInjectorResult() {
    AtomicInteger veryExpensiveInjectorInvocationCount = new AtomicInteger();
    LightJavaCodeInsightTestFixture myFixture = getFixture();
    InjectedLanguageManager.getInstance(myFixture.getProject()).registerMultiHostInjector(new MultiHostInjector() {
      @Override
      public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        veryExpensiveInjectorInvocationCount.incrementAndGet();
      }

      @NotNull
      @Override
      public List<Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(PsiLiteralExpressionImpl.class);
      }
    }, myFixture.getTestRootDisposable());
    ReadAction.runBlocking(() -> {
      PsiFile file = PsiFileFactory.getInstance(myFixture.getProject())
        .createFileFromText("a.java", JavaLanguage.INSTANCE, """
          class C { String s = ""; }""", true, false);
      PsiLiteralExpression literal =
        PsiTreeUtil.findElementOfClassAtOffset(file, file.getText().indexOf('"'), PsiLiteralExpression.class, false);
      assertEquals(0, veryExpensiveInjectorInvocationCount.get());

      assertNull(InjectedLanguageManager.getInstance(myFixture.getProject()).getInjectedPsiFiles(literal));
      assertEquals(1, veryExpensiveInjectorInvocationCount.get());

      assertNull(InjectedLanguageManager.getInstance(myFixture.getProject()).getInjectedPsiFiles(literal));
      assertEquals(1, veryExpensiveInjectorInvocationCount.get());
    });
  }

  static class MyATagAnnotatorWithQuickFix extends DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof XmlTag tag && tag.getName().equals("a")) {
        holder.newAnnotation(HighlightSeverity.ERROR, "aaaa").newFix(new AbstractIntentionAction() {
          @Override
          public @NotNull String getText() {
            return "myAAAFix";
          }

          @Override
          public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {

          }
        }).range(new ProperTextRange(1,2).shiftRight(tag.getTextRange().getStartOffset())).registerFix()
          .create();
        iDidIt();
      }
    }
  }

  @Test
  public void testAnnotatorGetCorrectOffsetsInItsQuickFix() {
    @org.intellij.lang.annotations.Language("JAVA")
    String text = """
      class C {
        String xmlWithLongPrefixSuffix = "<xml1><a><caret></a></xml1>";
      }
      """;
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("test.java", text);
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();

    assertInjected(true);
    assertTrue(ReadAction.computeBlocking(() -> myFixture.getFile().getText()).contains("aaaaaaaaaa")); // check that prefix/suffix are injected too, see MyTestInjector

    daemon.bringRealEditorBack();
    assertFalse(ReadAction.computeBlocking(() -> myFixture.getFile().getText()).contains("aaaaaaaaaa"));
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(XMLLanguage.INSTANCE, new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyATagAnnotatorWithQuickFix()},()->{
      daemon.type("x");
      daemon.doHighlighting();
      EdtTestUtil.runInEdtAndWait(() -> {
        int expectedFixOffset = myFixture.getEditor().getDocument().getText().indexOf("<a>") + 1;
        myFixture.getEditor().getCaretModel().moveToOffset(expectedFixOffset-1);
        assertNull(daemon.findActionWithText("myAAAFix"));
        myFixture.getEditor().getCaretModel().moveToOffset(expectedFixOffset);
        assertNotNull(daemon.findActionWithText("myAAAFix"));
        myFixture.getEditor().getCaretModel().moveToOffset(expectedFixOffset+1);
        assertNotNull(daemon.findActionWithText("myAAAFix"));
        myFixture.getEditor().getCaretModel().moveToOffset(expectedFixOffset+2);
        assertNull(daemon.findActionWithText("myAAAFix"));
      });
    });
  }
}
