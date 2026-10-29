package com.intellij.java.injection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
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
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.TestDataProvider;
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

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@SkipSlowTestLocally
public class JavaInjectedLanguageEditingTest extends InjectedLanguageTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  public Object getData(@NotNull String dataId) {
    return getProject() == null || getProject().isDisposed() ? null : TestDataProvider.withRules(getProject()).getData(dataId);
  }

  public void testHighlightJavaReferencedFromInjected() {
    configureFromFileText("x.java", """
      package x.y.z; class X { String xml="<x>x.y.z.<caret>X</x>"; }""");
    registerClassReferenceInXmlTokenAndRun(() -> checkUsagesHighlighted(2, "X"));
  }

  private void checkUsagesHighlighted(int num, @NonNls String usage) {
    ctrlShiftF7();
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    bringRealEditorBack();
    RangeHighlighter[] highlighters = getEditor().getMarkupModel().getAllHighlighters();
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
    assertSize(num, l);
    for (RangeHighlighter highlighter : highlighters) {
      String text = getFile().getText().substring(highlighter.getStartOffset(), highlighter.getEndOffset());
      assertEquals(usage, text);
    }
    if (num != 0) {
      executeAction(IdeActions.ACTION_EDITOR_ESCAPE);
    }
    highlighters = getEditor().getMarkupModel().getAllHighlighters();
    assertEmpty(highlighters);
  }

  public void testFindUsagesOfJavaReferencedFromInjected() {
    configureFromFileText("x.java", """
      package x.y.z; class X { String xml="<x>x.y.z.<caret>X</x>"; }""");
    registerClassReferenceInXmlTokenAndRun(() -> {
      FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(getProject())).getFindUsagesManager();
      PsiElement element1 = getFile();
      PsiClass aClass = ((PsiJavaFile)InjectedLanguageManager.getInstance(element1.getProject()).getTopLevelFile(element1)).getClasses()[0];
      FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(aClass, false);
      assertNotNull(handler);
      List<UsageInfo> infos = new ArrayList<>();
      handler.processElementUsages(aClass, new CommonProcessors.CollectProcessor<>(infos), handler.getFindUsagesOptions());
      UsageInfo info = assertOneElement(infos);
      PsiFile file = info.getFile();
      assertEquals(XMLLanguage.INSTANCE, file.getLanguage());
      PsiElement element = info.getElement();
      assertInstanceOf(element, XmlToken.class);
      assertEquals(9, info.getNavigationOffset());
    });
  }

  public void testHighlightAndUnhighlightUsagesInFile() {
    configureFromFileText("x.java", """
      class X { String xml = "<x>java.lang.Runnable</x>";  <caret>Runnable l;  }""");
    checkUsagesHighlighted(2, "Runnable");
    ctrlShiftF7();
    checkUsagesHighlighted(0, null);
  }

  public void testHighlightAndUnhighlightUsagesInInjectedFile() {
    configureFromFileText("x.java", """
      class X { String xml = "<x>java.lang.<caret>Runnable</x>";  Runnable l;  }""");
    ctrlShiftF7();
    assertSize(2, getEditor().getMarkupModel().getAllHighlighters());
    ctrlShiftF7();
    assertEmpty(getEditor().getMarkupModel().getAllHighlighters());
  }

  private void doTestFindUsagesAvailable() {
    DataContext dataContext = ((EditorEx)getEditor()).getDataContext();
    for (String actionId : ContainerUtil.ar(IdeActions.ACTION_FIND_USAGES, ShowUsagesAction.ID)) {
      AnAction action = ActionManager.getInstance().getAction(actionId);
      assertNotNull(actionId + " action not found", action);
      AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContext());
      ActionUtil.updateAction(action, event);
      assertTrue(actionId + "' action is disabled", event.getPresentation().isEnabled());
    }
    // enabled actions can show "nothing to work on" hint when performed, make sure it's not the case
    DataContext injectedDataContext = AnActionEvent.getInjectedDataContext(dataContext);
    Collection<SearchTarget> targets1 = FindUsagesAction.SEARCH_TARGETS.getData(injectedDataContext);
    UsageTarget[] targets2 = UsageView.USAGE_TARGETS_KEY.getData(injectedDataContext);
    assertFalse((targets1 == null || targets1.isEmpty()) &&
                (targets2 == null || targets2.length == 0));
  }

  public void testFindUsagesAvailable2() {
    configureFromFileText("x.java", """
      class XXX { String java = "public class YYY<caret> {{new YYY();}} "; }""");
    doTestFindUsagesAvailable();
  }

  public void testCtrlWWithEscapingAtEnd() {
    configureFromFileText("a.java", """
      class A { String a = "<caret>aaa\\"bbb\\"ccc\\""; }""");
    ctrlW();
    assertEquals("aaa", getEditor().getSelectionModel().getSelectedText());
    ctrlW();
    String expected = """
      aaa\\"bbb\\"ccc\\\"""";
    assertEquals(expected, getEditor().getSelectionModel().getSelectedText());
  }

  public void testFindUsagesDoesNotReportTextOccurrenceInInjectedContext() {
    configureFromFileText("x.java", """
      class XXX { String x = "XXX"; String xml = "XXX"; }""");
    final PsiClass psiClass = ((PsiJavaFile)getFile()).getClasses()[0];

    assertEmpty(ReadAction.computeBlocking(() -> ReferencesSearch.search(psiClass).findAll()));

    registerClassReferenceInXmlTokenAndRun(() -> {
      assertSize(1, ReadAction.computeBlocking(() -> ReferencesSearch.search(psiClass).findAll())); //in xml tag injected into "xml"

      // make string literal reference class
      registerClassReferenceInStringLiteralAndRun(() -> {
        assertSize(2, ReadAction.computeBlocking(() -> ReferencesSearch.search(psiClass).findAll()));  // in string literal also
      });
    });
  }

  private void registerClassReferenceInXmlTokenAndRun(@NotNull Runnable run) {
    PsiReferenceRegistrarImpl referenceProvidersRegistry =
      (PsiReferenceRegistrarImpl)ReferenceProvidersRegistry.getInstance().getRegistrar(XMLLanguage.INSTANCE);
    PsiReferenceProvider classReferenceProvider = CommonReferenceProviderTypes.getInstance().getClassReferenceProvider();
    try {
      referenceProvidersRegistry.registerReferenceProvider(PlatformPatterns.psiElement(XmlToken.class), classReferenceProvider);
      getPsiManager().dropPsiCaches();
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
      getPsiManager().dropPsiCaches();
      run.run();
    }
    finally {
      referenceProvidersRegistry.unregisterReferenceProvider(PsiLiteralExpression.class, classReferenceProvider);
    }
  }

  public void testCommentInInjection() {
    configureFromFileText("X.java", """
      class X { String text="bla<caret>bla"; }""");
    PsiElement element = PsiUtilBase.getElementAtCaret(getEditor());
    assertNotNull(element);
    PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)element.getParent();
    boolean result =
      TemporaryPlacesRegistry.getInstance(getProject()).getLanguageInjectionSupport().addInjectionInPlace(PlainTextLanguage.INSTANCE, host);
    assertTrue(result);
    PsiElement injected = InjectedLanguageUtil.findElementInInjected(host, 0);
    assertNotNull(injected);
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE);
    assertEquals("""
                   //class X { String text="blabla"; }""", getEditor().getDocument().getText());
  }

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
    InjectedLanguageManager.getInstance(getProject()).registerMultiHostInjector(injector, getTestRootDisposable());
    configureFromFileText("a.java", """
      class X { String xxx = "<caret>vvv";}""");
    Editor editor = getEditor();
    assertTrue(editor instanceof EditorWindow);
    Document document = editor.getDocument();
    assertEquals(0, document.getLineStartOffset(0));
    assertEquals(2, document.getLineStartOffset(1));
    assertEquals(8, document.getLineStartOffset(2));
    assertEquals(9, document.getLineStartOffset(3));
  }

  public void testNullInjectionDoesNotGetCachedForever() {
    configureFromFileText("X.java", """
      class X{ @org.intellij.lang.annotations.Language("XML")
       <caret>String xxx = "<x/>";
      }""");
    bringRealEditorBack();
    assertInjected(true);
    type("/");
    assertInjected(false);
    type("/");
    assertInjected(false);

    backspace();
    assertInjected(false);
    backspace();
    assertInjected(true);
  }

  private void assertInjected(boolean mustbe) {
    List<HighlightInfo> infos = doHighlighting();
    HighlightInfo inj = ContainerUtil.find(infos, info -> info.type == HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT);
    assertEquals("Injected info: " + infos, mustbe, inj != null);
  }

  public void testRangeHighlightersOutsideVisibleAreaAreReused() {
    @org.intellij.lang.annotations.Language("JAVA")
    @NonNls String text = "class X{ " +
                  "String[] s = String.valueOf(1).split(\"1,\"); \n"+
                  StringUtil.repeatSymbol('\n',500)+
                  "// xxx<caret>"+
                  StringUtil.repeatSymbol('\n',500)+
                  "String[] s2 = String.valueOf(2).split(\"2,\");\n" +
                  "}";
    configureFromFileText("X.java", text);

    ((EditorImpl)getEditor()).getScrollPane().getViewport().setSize(1000, 1000);
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    ((EditorImpl)getEditor()).getScrollPane().getViewport().setViewPosition(new Point(0, 1000));
    ((EditorImpl)getEditor()).getScrollPane().getViewport().setExtentSize(new Dimension(100, ((EditorImpl)getEditor()).getPreferredHeight() - 1000)); // include all till the end
    @NotNull Editor editor = getEditor();
    ProperTextRange visibleRange = editor.calculateVisibleRange();
    assertTrue(visibleRange.getStartOffset() > 0);

    assertInjected(true);
    MarkupModel markup = DocumentMarkupModel.forDocument(getEditor().getDocument(), getProject(), true);
    AtomicInteger addedOutside = new AtomicInteger();
    AtomicInteger deletedOutside = new AtomicInteger();
    ((MarkupModelImpl)markup).addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener() {
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        if (highlighter.getStartOffset() <= 50 || highlighter.getStartOffset() >= getEditor().getDocument().getTextLength() - 50) {
          addedOutside.incrementAndGet();
        }
      }

      @Override
      public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
        if (highlighter.getStartOffset() <= 50 || highlighter.getStartOffset() >= getEditor().getDocument().getTextLength() - 50) {
          deletedOutside.incrementAndGet();
        }
      }
    });

    type("y");
    assertInjected(true);
    assertEquals(0, addedOutside.get());
    assertEquals(0, deletedOutside.get());
  }

  @NotNull
  private List<DocumentWindow> getAllCachedInjections(PsiFile file) {
    PsiFile topLevelFile = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    return InjectedLanguageManager.getInstance(getProject()).getCachedInjectedDocumentsInRange(topLevelFile, topLevelFile.getTextRange());
  }

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
    InjectedLanguageManager.getInstance(getProject()).registerMultiHostInjector(injector, getTestRootDisposable());
    int N = 200;
    String text = "class X {\n" +
                  "  String multiInjectHtml = " +
                  "    \"<caret><inject/> " + StringUtil.repeat("<inject/>", N) + "\"; }";
    configureFromFileText("x.java", text);
    assertTrue(getEditor() instanceof EditorWindow);
    bringRealEditorBack();
    doHighlighting();
    assertSize(N+1, getAllCachedInjections(getFile()));
    final Set<SmartPsiElementPointer> pointers = new HashSet<>();
    for (DocumentWindow window : getAllCachedInjections(getFile())) {
      PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(window);
      file.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          super.visitElement(element);
          if (element instanceof XmlTag) {
            pointers.add(SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(element));
          }
        }
      });
    }
    final PsiLiteralExpression literalExpression =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLiteralExpression.class);
    for (int i=0; i<10; i++) {
      pointers.forEach(ptr -> assertNotNull(ptr.getElement()));
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      List<DocumentWindow> cached = getAllCachedInjections(getFile());
      assertSize(N+1, cached);
      final AtomicInteger literalMarkers = new AtomicInteger();
      final TextRange newLiteralRange = literalExpression.getTextRange().grown(1); // type space
      type(" ");
      ((DocumentImpl)getEditor().getDocument()).processRangeMarkersOverlappingWith(newLiteralRange.getStartOffset(),
                                                                                   newLiteralRange.getEndOffset(),
                                                                                   marker -> {
                                                                                     if (newLiteralRange.equalsToRange(marker.getStartOffset(), marker.getEndOffset())) {
                                                                                       literalMarkers.incrementAndGet();
                                                                                     }
                                                                                     return true;
                                                                                   });

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments(); //todo remove and see exceptions
      doHighlighting();                                                  //todo remove and see exceptions

      Editor editor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(getEditor(), getFile());
      assertTrue(editor instanceof EditorWindow);

      assertTrue(String.valueOf(literalMarkers), literalMarkers.get() < N);
    }
  }

  public void testCommitAfterReplaceTextAfterJoinLiteralsDoesntCrash() {
    configureByFile("/injection/javaConcat.java");
    invoke(new JoinConcatenatedStringLiteralsIntention().asIntention());

    WriteCommandAction.runWriteCommandAction(getProject(), () -> getDocument(getFile()).setText(getDocument(getFile()).getText()));

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    bringRealEditorBack();
    assertInjected(true);
  }

  public void testSuppressWithNonNlsCommentCrashesOnInjectionWithSuffix() {
    @org.intellij.lang.annotations.Language("JAVA")
    String text = """
      class s {\
            void g(@org.intellij.lang.annotations.Language(value = "JAVA", prefix = "class Dummy {", suffix = "}") String s1, String s) {}\
              {g("void x(<caret>String s) {}",
                      "ddd");}\
      }""";
    configureFromFileText("test.java", text);
    I18nInspection inspection = new I18nInspection();
    SuppressIntentionAction action = inspection.getSuppressActions(null)[0];
    assertEquals("Suppress with 'NON-NLS' comment", action.getText());
    assertTrue(action.isAvailable(getProject(), getEditor(), getFile()));

    WriteCommandAction.runWriteCommandAction(getProject(), ()->action.invoke(getProject(), getEditor(), getFile()));

    checkResultByText("""
                        class s {\
                              void g(@org.intellij.lang.annotations.Language(value = "JAVA", prefix = "class Dummy {", suffix = "}") String s1, String s) {}\
                                {g("void x(<caret>String s) {}", //NON-NLS
                                        "ddd");}\
                        }""");
  }

  public void testTyping() {
    @org.intellij.lang.annotations.Language("XML")
    String xml = "<input type='text'/>";
    @org.intellij.lang.annotations.Language("JAVA")
    String fileText = "class XXX { \n" +
                      "  void f() {\n" +
                      "    String xml = \"<caret>" + xml + "\";\n" +
                      "  }\n" +
                      "}";
    configureFromFileText("x.java", fileText);
    assertInjected(true);
    assertEquals(xml, getFile().getText());
    assertEmpty(highlightErrors());
    type("  ");
    assertEmpty(highlightErrors());
    assertEquals("  "+xml, getFile().getText());
  }

  public void testSmartBackspaceWithInjectionPrefix() {
    InjectedLanguageManager.getInstance(getProject()).registerMultiHostInjector(new MultiHostInjector() {
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
    }, getTestRootDisposable());
    configureFromFileText("test.java", """
      class C { String javaPr = " <caret>int a = 1;"; }""");
    bringRealEditorBack(); // backspace action is invoked on host editor, it finds injected editor itself
    backspace();
    checkResultByText("""
                        class C { String javaPr = "<caret>int a = 1;"; }""");
  }

  public void testCachedValuesDependentOnInjectedFilesMustBeInvalidatedOnPsiChangeInNonPhysicalHost() {
    @org.intellij.lang.annotations.Language("JAVA")
    String fileText = """
      class XXX {\s
        void f() {
          String xml = "b";
        }
      }""";

    PsiFile hostFile = (PsiFile)PsiFileFactory.getInstance(getProject()).createFileFromText("a.java", JavaLanguage.INSTANCE, fileText).copy();
    int offset = fileText.indexOf("b\"");
    PsiFile injectedFile = InjectedLanguageUtilBase.findInjectedPsiNoCommit(hostFile, offset);
    assertNotNull(injectedFile);


    CachedValue<String> cv = CachedValuesManager.getManager(getProject())
      .createCachedValue(() -> CachedValueProvider.Result.create(injectedFile.getText(), injectedFile));

    assertEquals("b", cv.getValue());

    ((LeafElement)hostFile.findElementAt(offset)).replaceWithText("\"ab\"");

    assertSame(injectedFile, InjectedLanguageUtilBase.findInjectedPsiNoCommit(hostFile, offset));
    assertTrue(injectedFile.isValid());
    assertEquals("ab", injectedFile.getText());
    assertEquals("ab", cv.getValue());
  }

  public void testMoveElementLeftAction() {
    configureFromFileText("test.java", """
      class C { String java = "class I { int a = 1 + 2<caret>; }"; }""");
    bringRealEditorBack();
    executeAction(IdeActions.MOVE_ELEMENT_LEFT);
    checkResultByText("""
                        class C { String java = "class I { int a = 2<caret> + 1; }"; }""");
  }

  public void testCacheNegativeInjectorResult() {
    AtomicInteger veryExpensiveInjectorInvocationCount = new AtomicInteger();
    InjectedLanguageManager.getInstance(getProject()).registerMultiHostInjector(new MultiHostInjector() {
      @Override
      public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        veryExpensiveInjectorInvocationCount.incrementAndGet();
      }

      @NotNull
      @Override
      public List<Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(PsiLiteralExpressionImpl.class);
      }
    }, getTestRootDisposable());
    PsiFile file = PsiFileFactory.getInstance(getProject())
      .createFileFromText("a.java", JavaLanguage.INSTANCE, """
        class C { String s = ""; }""", true, false);
    PsiLiteralExpression literal =
      PsiTreeUtil.findElementOfClassAtOffset(file, file.getText().indexOf('"'), PsiLiteralExpression.class, false);
    assertEquals(0, veryExpensiveInjectorInvocationCount.get());

    assertNull(InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(literal));
    assertEquals(1, veryExpensiveInjectorInvocationCount.get());

    assertNull(InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(literal));
    assertEquals(1, veryExpensiveInjectorInvocationCount.get());
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

  public void testAnnotatorGetCorrectOffsetsInItsQuickFix() {
    @org.intellij.lang.annotations.Language("JAVA")
    String text = """
      class C {
        String xmlWithLongPrefixSuffix = "<xml1><a><caret></a></xml1>";
      }
      """;
    configureFromFileText("test.java", text);

    assertInjected(true);
    assertTrue(getFile().getText().contains("aaaaaaaaaa")); // check that prefix/suffix are injected too, see MyTestInjector

    bringRealEditorBack();
    assertFalse(getFile().getText().contains("aaaaaaaaaa"));
    DaemonAnnotatorsRespondToChangesTest.useAnnotatorsIn(XMLLanguage.INSTANCE, new DaemonAnnotatorsRespondToChangesTest.MyRecordingAnnotator[]{new MyATagAnnotatorWithQuickFix()},()->{
      type("x");
      doHighlighting();
      int expectedFixOffset = getEditor().getDocument().getText().indexOf("<a>") + 1;
      getEditor().getCaretModel().moveToOffset(expectedFixOffset-1);
      assertNull(findActionWithText("myAAAFix"));
      getEditor().getCaretModel().moveToOffset(expectedFixOffset);
      assertNotNull(findActionWithText("myAAAFix"));
      getEditor().getCaretModel().moveToOffset(expectedFixOffset+1);
      assertNotNull(findActionWithText("myAAAFix"));
      getEditor().getCaretModel().moveToOffset(expectedFixOffset+2);
      assertNull(findActionWithText("myAAAFix"));
    });
  }
}
