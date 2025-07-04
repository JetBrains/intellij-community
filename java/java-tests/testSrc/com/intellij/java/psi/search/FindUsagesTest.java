// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.search;

import com.intellij.JavaTestUtil;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class FindUsagesTest extends JavaPsiTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/findUsages/" + getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    createTestProjectStructure(root);
  }

  public void testOverloadConstructors() {
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
  }

  public void testSiblingImplement() {
    PsiClass anInterface = myJavaFacade.findClass("A.I", GlobalSearchScope.allScope(myProject));
    PsiMethod method = anInterface.getMethods()[0];
    final Collection<PsiMethod> overriders = OverridingMethodsSearch.search(method).findAll();
    assertEquals(1, overriders.size());
  }

  public void testSiblingFindUsages() {
    PsiClass xv = myJavaFacade.findClass("XValueContainerNode", GlobalSearchScope.allScope(myProject));
    PsiMethod method = xv.findMethodsByName("setObsolete", false)[0];

    FindUsagesHandler handler = ((FindManagerImpl)FindManager.getInstance(getProject())).getFindUsagesManager()
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
  }

  public void testProtectedMethodInPackageLocalClass() {
    PsiMethod method = myJavaFacade.findClass("foo.PackageLocal", GlobalSearchScope.allScope(myProject)).getMethods()[0];
    assertEquals(1, OverridingMethodsSearch.search(method).findAll().size());
    assertEquals(1, ReferencesSearch.search(method).findAll().size());
  }

  public void testLibraryClassUsageFromDecompiledSource() {
    PsiElement decompiled =
      ((PsiCompiledElement)myJavaFacade.findClass("javax.swing.JLabel", GlobalSearchScope.allScope(myProject))).getMirror();
    assertEquals(2, ReferencesSearch.search(decompiled, GlobalSearchScope.projectScope(myProject)).findAll().size());
  }

  public void testImplicitConstructorUsage() {
    PsiMethod[] ctrs = myJavaFacade.findClass("Foo", GlobalSearchScope.allScope(myProject)).getConstructors();
    PsiMethod method = ctrs[0];
    assertEquals(0, method.getParameterList().getParametersCount());
    assertEquals(0, ReferencesSearch.search(method).findAll().size());

    PsiMethod usedMethod = ctrs[1];
    assertEquals(1, usedMethod.getParameterList().getParametersCount());
    assertEquals(1, ReferencesSearch.search(usedMethod).findAll().size());
  }

  public void testImplicitVarArgsConstructorsUsage() {
    PsiMethod[] ctrs = myJavaFacade.findClass("A1", GlobalSearchScope.allScope(myProject)).getConstructors();
    PsiMethod usedCtr = ctrs[0];
    assertEquals("java.lang.String", ((PsiEllipsisType)usedCtr.getParameterList().getParameters()[0].getType()).getComponentType().getCanonicalText());
    assertEquals(1, ReferencesSearch.search(usedCtr).findAll().size());

    PsiMethod unusedCtr = ctrs[1];
    assertEquals("java.lang.Object", ((PsiEllipsisType)unusedCtr.getParameterList().getParameters()[0].getType()).getComponentType().getCanonicalText());
    assertEquals(0, ReferencesSearch.search(unusedCtr).findAll().size());
  }

  private static void addReference(@NotNull PsiReference ref, @NotNull List<? super PsiFile> filesList, @NotNull IntList startsList, @NotNull IntList endsList) {
    PsiElement element = ref.getElement();
    filesList.add(element.getContainingFile());
    TextRange range = element.getTextRange();
    TextRange rangeInElement = ref.getRangeInElement();
    startsList.add(range.getStartOffset() + rangeInElement.getStartOffset());
    endsList.add(range.getStartOffset() + rangeInElement.getEndOffset());
  }

  public void testFieldInJavadoc() {
    PsiClass aClass = myJavaFacade.findClass("A", GlobalSearchScope.allScope(myProject));
    PsiField field = aClass.findFieldByName("FIELD", false);
    doTest(field, new String[]{"A.java"}, new int[]{}, new int[]{});
  }

  public void testXml() {
    PsiClass aClass = myJavaFacade.findClass("com.Foo", GlobalSearchScope.allScope(myProject));
    doTest(aClass, new String[]{"Test.xml"}, new int[]{32}, new int[]{35});

    final PsiFile nonCodeUsage = PsiFileFactory.getInstance(myProject).createFileFromText("a.xml", XmlFileType.INSTANCE, "<root action='com.Foo'/>", 0, true);
    assertTrue(new UsageInfo(nonCodeUsage, 14, 21, true).getNavigationOffset() > 0);
  }

  public void testNonCodeClassUsages() throws Exception {
    final TempDirTestFixture tdf = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    tdf.setUp();

    try {
      WriteCommandAction.writeCommandAction(getProject()).run(() -> {
        final ModifiableModuleModel moduleModel = ModuleManager.getInstance(getProject()).getModifiableModel();
        moduleModel.newModule("independent/independent.iml", JavaModuleType.getModuleType().getId());
        moduleModel.commit();

        tdf.createFile("plugin.xml", """
          <document>
            <action class="com.Foo" />
            <action class="com.Foo.Bar" />
            <action class="com.Foo$Bar" />
          </document>""");

        PsiTestUtil.addContentRoot(ModuleManager.getInstance(getProject()).findModuleByName("independent"), tdf.getFile(""));
      });

      GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
      PsiClass foo = myJavaFacade.findClass("com.Foo", scope);
      PsiClass bar = myJavaFacade.findClass("com.Foo.Bar", scope);

      final int[] count = {0};
      Processor<UsageInfo> processor = usageInfo -> {
        int navigationOffset = usageInfo.getNavigationOffset();
        assertTrue(navigationOffset > 0);
        String textAfter = usageInfo.getFile().getText().substring(navigationOffset);
        assertTrue(textAfter, textAfter.startsWith("Foo") || textAfter.startsWith("Bar") ||
                              textAfter.startsWith("com.Foo.Bar") // sorry, can't get references with dollar-dot mismatch to work now
        );
        count[0]++;
        return true;
      };
      JavaFindUsagesHandler handler = new JavaFindUsagesHandler(bar, JavaFindUsagesHandlerFactory.getInstance(getProject()));

      count[0] = 0;
      handler.processUsagesInText(foo, processor, scope);
      assertEquals(3, count[0]);

      count[0] = 0;
      handler.processUsagesInText(bar, processor, scope);
      assertEquals(2, count[0]);
    }
    finally {
      tdf.tearDown();
    }
  }

  public void testImplicitToString() {
    PsiClass aClass = myJavaFacade.findClass("Foo");
    assertNotNull(aClass);
    PsiMethod toString = assertOneElement(aClass.findMethodsByName("toString", false));

    JavaFindUsagesHandlerFactory factory = JavaFindUsagesHandlerFactory.getInstance(myProject);
    int[] count = {0};
    factory.createFindUsagesHandler(toString, false).processElementUsages(toString, info -> {
      count[0]++;
      return true;
    }, factory.getFindMethodOptions());
    assertEquals(1, count[0]);
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

    assertEquals("Usages don't match", expected, actual);
  }

  public void testFindUsagesMustInterrupt/*DuringLongButInterruptibleResolveInsideReadAction*/() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("x.Ref", GlobalSearchScope.allScope(myProject));
    PsiField field = Objects.requireNonNull(aClass).findFieldByName("ref", false);
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
          usages.set(ReferencesSearch.search(field, scope).findAll());
        }, new EmptyProgressIndicator())
      );

      while(!resolveStarted.get() && !future.isDone()) {
        UIUtil.dispatchAllInvocationEvents();
      }

      WriteAction.run(() -> toSleepMs.set(0));

      future.get();
      assertEquals(2, usages.get().size());
    }
    finally {
      registrar.unregisterReferenceProvider(PsiLiteralExpression.class, hardProvider);
    }
  }

  public void testFindUsagesMustNotSwallow/*IndexNotReadyException*/() throws ExecutionException, InterruptedException {
    PsiClass aClass = myJavaFacade.findClass("x.Ref", GlobalSearchScope.allScope(myProject));
    PsiField field = Objects.requireNonNull(aClass).findFieldByName("ref", false);
    SearchScope scope = new LocalSearchScope(aClass);
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() ->
        ProgressManager.getInstance().runProcess(() ->
            assertThrows(IndexNotReadyException.class, () -> {
              SearchRequestCollector collector = new SearchRequestCollector(new SearchSession(field));
              collector.searchWord(field.getName(), scope, true, field);
              PsiSearchHelper.getInstance(getProject()).processRequests(collector, reference -> {
                  throw IndexNotReadyException.create();
                });
            })
          , new EmptyProgressIndicator())
      );

    future.get();
  }
}
