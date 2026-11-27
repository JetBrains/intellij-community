// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.search;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.JavaClassFindUsagesOptions;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiReferenceProcessorAdapter;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.SearchSession;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.siyeh.ig.psiutils.PsiElementOrderComparator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class FindUsagesTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private Project myProject;
  private JavaPsiFacadeEx myJavaFacade;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    myProject = fixture.getProject();
    myJavaFacade = fixture.getJavaFacade();
    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/findUsages/" + fixture.getTestName(true);
    PsiTestUtil.removeAllRoots(fixture.getModule(), IdeaTestUtil.getMockJdk17());
    fixture.createTestProjectStructure(root);
  }

  @Test
  public void testOverloadConstructors() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = myJavaFacade.findClass("B", GlobalSearchScope.allScope(myProject));
      PsiMethod constructor = aClass.findMethodsByName("B", false)[0];
      PsiMethodCallExpression superCall = (PsiMethodCallExpression) constructor.getBody().getStatements()[0].getFirstChild();
      PsiReferenceExpression superExpr = superCall.getMethodExpression();
      String[] fileNames = {"B.java", "A.java", "A.java", "B.java"};
      int[] starts = {};
      int[] ends = {};
      final ArrayList<PsiFile> filesList = new ArrayList<>();
      final IntList startsList = new IntArrayList();
      final IntList endsList = new IntArrayList();
      PsiReference[] refs =
        MethodReferencesSearch.search((PsiMethod)superExpr.resolve(), GlobalSearchScope.projectScope(myProject), false).toArray(PsiReference.EMPTY_ARRAY);
      for (PsiReference ref : refs) {
        addReference(ref, filesList, startsList, endsList);
      }
      checkResult(fileNames, filesList, starts, startsList, ends, endsList);
    });
  }

  @Test
  public void testSiblingImplement() {
    ReadAction.runBlocking(() -> {
      PsiClass anInterface = myJavaFacade.findClass("A.I", GlobalSearchScope.allScope(myProject));
      PsiMethod method = anInterface.getMethods()[0];
      final Collection<PsiMethod> overriders = ReadAction.computeBlocking(() -> OverridingMethodsSearch.search(method).findAll());
      assertEquals(1, overriders.size());
    });
  }

  @Test
  public void testSiblingFindUsages() {
    ReadAction.runBlocking(() -> {
      PsiClass xv = myJavaFacade.findClass("XValueContainerNode", GlobalSearchScope.allScope(myProject));
      PsiMethod method = xv.findMethodsByName("setObsolete", false)[0];

      FindUsagesHandler handler = ((FindManagerImpl)FindManager.getInstance(myProject)).getFindUsagesManager()
        .getFindUsagesHandler(method, FindUsagesHandlerFactory.OperationMode.USAGES_WITH_DEFAULT_OPTIONS);

      PsiElement[] elements = handler.getPrimaryElements();
      int[] count = {0};
      for (PsiElement element : elements) {
        handler.processElementUsages(element, info -> {
          count[0]++;
          PsiClass containing = PsiTreeUtil.getParentOfType(info.getElement(), PsiClass.class);
          assertEquals("Use", containing.getName());

          return true;
        }, handler.getFindUsagesOptions());
      }
      assertEquals(1, count[0]);
    });
  }

  @Test
  public void testProtectedMethodInPackageLocalClass() {
    ReadAction.runBlocking(() -> {
      PsiMethod method = myJavaFacade.findClass("foo.PackageLocal", GlobalSearchScope.allScope(myProject)).getMethods()[0];
      assertEquals(1, ReadAction.computeBlocking(()->OverridingMethodsSearch.search(method).findAll()).size());
      assertEquals(1, ReadAction.computeBlocking(()->ReferencesSearch.search(method).findAll()).size());
    });
  }

  @Test
  public void testLibraryClassUsageFromDecompiledSource() {
    ReadAction.runBlocking(() -> {
      PsiElement decompiled =
        BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() ->
          ((PsiCompiledElement)myJavaFacade.findClass("javax.swing.JLabel", GlobalSearchScope.allScope(myProject))).getMirror());
      assertEquals(2, ReadAction.computeBlocking(()->ReferencesSearch.search(decompiled, GlobalSearchScope.projectScope(myProject)).findAll()).size());
    });
  }
  
  @Test
  public void testFindConstructorUsagesFromClass() {
    JavaClassFindUsagesOptions options = new JavaClassFindUsagesOptions(myProject);
    options.isUsages = true;
    options.isConstructorUsages = true;
    doTestFindUsages("SomeClass", options, "this()");
    options.isUsages = false;
    doTestFindUsages("SomeClass", options, "this()");
    options.isUsages = true;
    options.isConstructorUsages = false;
    doTestFindUsages("SomeClass", options, "");
  }
  
  @Test
  public void testFindDefaultConstructorUsagesFromClass() {
    JavaClassFindUsagesOptions options = new JavaClassFindUsagesOptions(myProject);
    options.isUsages = false;
    options.isConstructorUsages = true;
    doTestFindUsages("HeadlessHorsewoman", options, "new HeadlessHorsewoman()");
  }

  private void doTestFindUsages(String className, JavaClassFindUsagesOptions options, String expected) {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.allScope(myProject));
      CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<>();
      FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(myProject)).getFindUsagesManager();
      FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(aClass, false);
      assertNotNull(handler);
      handler.processElementUsages(aClass, processor, options);
      Collection<UsageInfo> usages = processor.getResults();
      assertEquals(expected, StringUtil.join(usages, u -> u.getElement().getParent().getText() , "\n"));
    });
  }

  @Test
  public void testDefaultConstructor() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = myJavaFacade.findClass("Animal", GlobalSearchScope.allScope(myProject));
      PsiMethod main = aClass.findMethodsByName("main", false)[0];
      PsiLocalVariable variable = (PsiLocalVariable)main.getBody().getStatements()[0].getFirstChild();
      PsiNewExpression newExpression = (PsiNewExpression)variable.getInitializer();
      PsiMethod defaultConstructor = newExpression.resolveMethod();
      Collection<PsiReference> references =
        ReadAction.computeBlocking(() -> ReferencesSearch.search(defaultConstructor, GlobalSearchScope.projectScope(myProject)).findAll());
      List<@NotNull PsiElement> result = 
        references.stream().map(r -> r.getElement()).sorted(PsiElementOrderComparator.getInstance()).toList();
      assertEquals(2, references.size());
      assertInstanceOf(PsiNewExpression.class, result.get(0).getParent());
      assertTrue(result.get(1) instanceof PsiMethod m && m.isConstructor() && "OneLeggedDog".equals(m.getName()));
    });
  }

  @Test
  public void testImplicitConstructorUsage() {
    ReadAction.runBlocking(() -> {
      PsiMethod[] ctrs = myJavaFacade.findClass("Foo", GlobalSearchScope.allScope(myProject)).getConstructors();
      PsiMethod method = ctrs[0];
      assertEquals(0, method.getParameterList().getParametersCount());
      assertEquals(0, ReadAction.computeBlocking(()->ReferencesSearch.search(method).findAll()).size());

      PsiMethod usedMethod = ctrs[1];
      assertEquals(1, usedMethod.getParameterList().getParametersCount());
      assertEquals(1, ReadAction.computeBlocking(()->ReferencesSearch.search(usedMethod).findAll()).size());
    });
  }

  @Test
  public void testImplicitVarArgsConstructorsUsage() {
    ReadAction.runBlocking(() -> {
      PsiMethod[] ctrs = myJavaFacade.findClass("A1", GlobalSearchScope.allScope(myProject)).getConstructors();
      PsiMethod usedCtr = ctrs[0];
      assertEquals("java.lang.String", ((PsiEllipsisType)usedCtr.getParameterList().getParameters()[0].getType()).getComponentType().getCanonicalText());
      assertEquals(1, ReadAction.computeBlocking(()->ReferencesSearch.search(usedCtr).findAll()).size());

      PsiMethod unusedCtr = ctrs[1];
      assertEquals("java.lang.Object", ((PsiEllipsisType)unusedCtr.getParameterList().getParameters()[0].getType()).getComponentType().getCanonicalText());
      assertEquals(0, ReadAction.computeBlocking(()->ReferencesSearch.search(unusedCtr).findAll()).size());
    });
  }

  private static void addReference(@NotNull PsiReference ref, @NotNull List<? super PsiFile> filesList, @NotNull IntList startsList, @NotNull IntList endsList) {
    PsiElement element = ref.getElement();
    filesList.add(element.getContainingFile());
    TextRange range = element.getTextRange();
    TextRange rangeInElement = ref.getRangeInElement();
    startsList.add(range.getStartOffset() + rangeInElement.getStartOffset());
    endsList.add(range.getStartOffset() + rangeInElement.getEndOffset());
  }

  @Test
  public void testFieldInJavadoc() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = myJavaFacade.findClass("A", GlobalSearchScope.allScope(myProject));
      PsiField field = aClass.findFieldByName("FIELD", false);
      doTest(field, new String[]{"A.java"}, new int[]{}, new int[]{});
    });
  }

  @Test
  public void testXml() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = myJavaFacade.findClass("com.Foo", GlobalSearchScope.allScope(myProject));
      doTest(aClass, new String[]{"Test.xml"}, new int[]{32}, new int[]{35});

      final PsiFile nonCodeUsage = PsiFileFactory.getInstance(myProject).createFileFromText("a.xml", XmlFileType.INSTANCE, "<root action='com.Foo'/>", 0, true);
      assertTrue(new UsageInfo(nonCodeUsage, 14, 21, true).getNavigationOffset() > 0);
    });
  }

  @Test
  public void testNonCodeClassUsages() throws Exception {
    final TempDirTestFixture tdf = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    tdf.setUp();

    try {
      WriteCommandAction.writeCommandAction(myProject).run(() -> {
        final ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
        moduleModel.newModule("independent/independent.iml", JavaModuleType.getModuleType().getId());
        moduleModel.commit();

        tdf.createFile("plugin.xml", """
          <document>
            <action class="com.Foo" />
            <action class="com.Foo.Bar" />
            <action class="com.Foo$Bar" />
          </document>""");

        PsiTestUtil.addContentRoot(ModuleManager.getInstance(myProject).findModuleByName("independent"), tdf.getFile(""));
      });

      ReadAction.runBlocking(() -> {
        GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
        PsiClass foo = myJavaFacade.findClass("com.Foo", scope);
        PsiClass bar = myJavaFacade.findClass("com.Foo.Bar", scope);

        final int[] count = {0};
        Processor<UsageInfo> processor = usageInfo -> {
          int navigationOffset = usageInfo.getNavigationOffset();
          assertTrue(navigationOffset > 0);
          String textAfter = usageInfo.getFile().getText().substring(navigationOffset);
          assertTrue(textAfter.startsWith("Foo") || textAfter.startsWith("Bar") ||
                                textAfter.startsWith("com.Foo.Bar") // sorry, can't get references with dollar-dot mismatch to work now, textAfter
          );
          count[0]++;
          return true;
        };
        JavaFindUsagesHandler handler = new JavaFindUsagesHandler(bar, JavaFindUsagesHandlerFactory.getInstance(myProject));

        count[0] = 0;
        handler.processUsagesInText(foo, processor, scope);
        assertEquals(3, count[0]);

        count[0] = 0;
        handler.processUsagesInText(bar, processor, scope);
        assertEquals(2, count[0]);
      });
    }
    finally {
      tdf.tearDown();
    }
  }

  @Test
  public void testImplicitToString() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = myJavaFacade.findClass("Foo");
      assertNotNull(aClass);
      PsiMethod toString = assertThat(aClass.findMethodsByName("toString", false)).singleElement().actual();

      JavaFindUsagesHandlerFactory factory = JavaFindUsagesHandlerFactory.getInstance(myProject);
      int[] count = {0};
      factory.createFindUsagesHandler(toString, false).processElementUsages(toString, info -> {
        count[0]++;
        return true;
      }, factory.getFindMethodOptions());
      assertEquals(1, count[0]);
    });
  }

  public static void doTest(PsiElement element, String[] fileNames, int[] starts, int[] ends) {
    final ArrayList<PsiFile> filesList = new ArrayList<>();
    final IntList startsList = new IntArrayList();
    final IntList endsList = new IntArrayList();
    ReferencesSearch.search(element, GlobalSearchScope.projectScope(element.getProject()), false).forEach(new PsiReferenceProcessorAdapter(
      ref -> {
        addReference(ref, filesList, startsList, endsList);
        return true;
      }));

    checkResult(fileNames, filesList, starts, startsList, ends, endsList);

  }

  private static final class SearchResult implements Comparable<SearchResult> {
    String fileName;
    int startOffset;
    int endOffset;

    private SearchResult(final String fileName, final int startOffset, final int endOffset) {
      this.fileName = fileName;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }

    @Override
    public int compareTo(final SearchResult o) {
      int rc = fileName.compareTo(o.fileName);
      if (rc != 0) return rc;

      rc = startOffset - o.startOffset;
      if (rc != 0) return rc;

      return endOffset - o.endOffset;
    }

    @Override
    public String toString() {
      return fileName + "[" + startOffset + ":" + endOffset + "]";
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final SearchResult that = (SearchResult)o;

      if (endOffset != that.endOffset) return false;
      if (startOffset != that.startOffset) return false;
      if (fileName != null ? !fileName.equals(that.fileName) : that.fileName != null) return false;

      return true;
    }
  }

  private static void checkResult(String @NotNull [] fileNames, final List<? extends PsiFile> filesList, int[] starts, final IntList startsList, int[] ends, final IntList endsList) {
    List<SearchResult> expected = new ArrayList<>();
    for (int i = 0; i < fileNames.length; i++) {
      String fileName = fileNames[i];
      expected.add(new SearchResult(fileName, i < starts.length ? starts[i] : -1, i < ends.length ? ends[i] : -1));
    }

    List<SearchResult> actual = new ArrayList<>();
    for (int i = 0; i < filesList.size(); i++) {
      PsiFile psiFile = filesList.get(i);
      actual.add(
        new SearchResult(psiFile.getName(), i < starts.length ? startsList.getInt(i) : -1, i < ends.length ? endsList.getInt(i) : -1));
    }

    Collections.sort(expected);
    Collections.sort(actual);

    assertEquals(expected, actual, "Usages don't match");
  }

  @Test
  public void testFindUsagesMustInterrupt/*DuringLongButInterruptibleResolveInsideReadAction*/() throws Exception {
    PsiField field = ReadAction.computeBlocking(() -> {
      PsiClass aClass = myJavaFacade.findClass("x.Ref", GlobalSearchScope.allScope(myProject));
      return Objects.requireNonNull(aClass).findFieldByName("ref", false);
    });
    AtomicInteger toSleepMs = new AtomicInteger();
    AtomicBoolean resolveStarted = new AtomicBoolean();
    final PsiReferenceProvider hardProvider = new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        String text = String.valueOf(((PsiLiteralExpression)element).getValue());
        if (text.equals("ref")) {
          return new PsiReference[]{new PsiReferenceBase<>(element, false) {
            @Override
            public PsiElement resolve() {
              return field;
            }

            @Override
            public boolean isReferenceTo(@NotNull PsiElement element) {
              resolveStarted.set(true);
              ApplicationManager.getApplication().assertReadAccessAllowed();
              // emulate slow (but interruptible) resolve
              while (toSleepMs.addAndGet(-100) > 0) {
                ProgressManager.checkCanceled();
                TimeoutUtil.sleep(100);
              }
              return super.isReferenceTo(element);
            }
          }};
        }
        else {
          return PsiReference.EMPTY_ARRAY;
        }
      }
    };
    PsiReferenceRegistrarImpl registrar = (PsiReferenceRegistrarImpl)ReferenceProvidersRegistry.getInstance().getRegistrar(JavaLanguage.INSTANCE);
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class), hardProvider);
    toSleepMs.set(1_000_000);
    try {
      AtomicReference<Collection<PsiReference>> usages = new AtomicReference<>();
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() ->
        ProgressManager.getInstance().runProcess(() -> {
          GlobalSearchScope scope = ReadAction.compute(() -> GlobalSearchScope.fileScope(myProject, field.getContainingFile().getVirtualFile()));
          usages.set(ReadAction.nonBlocking(() -> ReferencesSearch.search(field, scope).findAll()).executeSynchronously());
        }, new EmptyProgressIndicator())
      );

      EdtTestUtil.runInEdtAndWait(() -> {
        while(!resolveStarted.get() && !future.isDone()) {
          PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        }
      });

      WriteAction.runAndWait(() -> toSleepMs.set(0));

      future.get();
      assertEquals(2, usages.get().size());
    }
    finally {
      registrar.unregisterReferenceProvider(PsiLiteralExpression.class, hardProvider);
    }
  }

  @Test
  public void testFindUsagesMustNotSwallow/*IndexNotReadyException*/() throws ExecutionException, InterruptedException {
    PsiClass aClass = ReadAction.computeBlocking(() -> myJavaFacade.findClass("x.Ref", GlobalSearchScope.allScope(myProject)));
    PsiField field = ReadAction.computeBlocking(() -> Objects.requireNonNull(aClass).findFieldByName("ref", false));
    SearchScope scope = new LocalSearchScope(aClass);
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() ->
        ProgressManager.getInstance().runProcess(() ->
            assertThrows(IndexNotReadyException.class, () -> {
              SearchRequestCollector collector = new SearchRequestCollector(new SearchSession(field));
              collector.searchWord(field.getName(), scope, true, field);
              PsiSearchHelper.getInstance(myProject).processRequests(collector, reference -> {
                  throw IndexNotReadyException.create();
                });
            })
          , new EmptyProgressIndicator())
      );

    future.get();
  }
}
