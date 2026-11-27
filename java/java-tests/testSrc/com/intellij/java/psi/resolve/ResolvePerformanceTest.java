// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.resolve;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@PerformanceUnitTest
@StressTestApplication
public class ResolvePerformanceTest {
  private static final Logger LOG = Logger.getInstance(ResolvePerformanceTest.class);

  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture =
    heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/psi/resolve/");

  @Test
  public void testPerformance1() throws Exception{
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    final String fullPath = JavaTestUtil.getJavaTestDataPath() + "/psi/resolve/Thinlet.java";
    final VirtualFile vFile = StandardFileSystems.local().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    final List<PsiReference> references = new ArrayList<>();
    assertNotNull(vFile, "file " + fullPath + " not found");
    //final int[] ints = new int[10000000];
    System.gc();

    String fileText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(vFile));
    PsiFile myFile = fixture.createFile(vFile.getName(), fileText);
    ReadAction.runBlocking(() -> myFile.accept(new JavaRecursiveElementWalkingVisitor(){
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression){
        references.add(expression);
        visitElement(expression);
      }
    }));
    LOG.debug("");
    LOG.debug("Found " + references.size() + " references");
    long time = System.currentTimeMillis();

    // Not cached pass
    resolveAllReferences(references);
    LOG.debug("Not cached resolve: " + (System.currentTimeMillis() - time)/(double)1000 + "s");

    WriteAction.runAndWait(() -> ResolveCache.getInstance(fixture.getProject()).clearCache(true));

    time = System.currentTimeMillis();
    // Cached pass
    resolveAllReferences(references);
    LOG.debug("Not cached resolve with cached repository: " + (System.currentTimeMillis() - time)/(double)1000 + "s");

    time = System.currentTimeMillis();
    // Cached pass
    resolveAllReferences(references);
    LOG.debug("Cached resolve: " + (System.currentTimeMillis() - time)/(double)1000 + "s");
 }

  @Test
  public void testPerformance2() throws Exception{
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    final String fullPath = JavaTestUtil.getJavaTestDataPath() + "/psi/resolve/ant/build.xml";
    final VirtualFile vFile = StandardFileSystems.local().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    final List<PsiReference> references = new ArrayList<>();
    assertNotNull(vFile, "file " + fullPath + " not found");

    String fileText = VfsUtilCore.loadText(vFile);
    fileText = StringUtil.convertLineSeparators(fileText);
    PsiFile myFile = fixture.createFile(vFile.getName(), fileText);
    ReadAction.runBlocking(() -> {
      myFile.accept(new XmlRecursiveElementVisitor(){
        @Override public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
          ContainerUtil.addAll(references, value.getReferences());
          super.visitXmlAttributeValue(value);
        }
      });

      myFile.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression){
          references.add(expression);
          visitElement(expression);
        }
      });
    });

    LOG.debug("");
    LOG.debug("Found " + references.size() + " references");

    resolveAllReferences(references);
  }

  private static void resolveAllReferences(List<PsiReference> list){
    ReadAction.runBlocking(() -> {
      for (PsiReference reference : list) {
        if (reference instanceof PsiJavaReference) {
          ((PsiJavaReference)reference).advancedResolve(false).isValidResult();
        }
        else {
          reference.resolve();
        }
      }
    });
  }

  @Test
  public void testStaticImportInTheSameClassPerformance() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    RecursionManager.disableMissedCacheAssertions(fixture.getTestRootDisposable());
    warmUpResolve();

    PsiReference ref = fixture.configureReferenceByFile("class/" + fixture.getTestName(false) + ".java");
    ensureIndexUpToDate();
    Benchmark.newBenchmark(fixture.getTestName(false), () -> ReadAction.runBlocking(() -> assertNull(ref.resolve())))
      .warmupIterations(20)
      .attempts(50)
      .start();
    // attempt.min.ms varies below the measurement threshold
  }

  @Test
  public void testResolveOfManyStaticallyImportedFields() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    int fieldCount = 7000;

    fixture.createFile(fixture.getModule(), fixture.createTempVirtualDir(), "Constants.java",
               "interface Constants { " +
               IntStreamEx.range(0, fieldCount).mapToObj(i -> "String field" + i + ";").joining("\n") +
               "}");

    PsiFile file = fixture.createFile(fixture.getModule(), fixture.createTempVirtualDir(), "a.java",
                              "import static Constants.*;\n" +
                              "class Usage { \n" +
                              "void foo(String s) {}\n" +
                              "{" +
                              IntStreamEx.range(0, fieldCount).mapToObj(i -> "foo(field" + i + ");").joining("\n") +
                              "}}");

    List<PsiJavaCodeReferenceElement> refs =
      ReadAction.computeBlocking(() -> SyntaxTraverser.psiTraverser(file).filter(PsiJavaCodeReferenceElement.class).toList());

    Benchmark.newBenchmark(fixture.getTestName(false), () -> ReadAction.runBlocking(() -> {
      for (PsiJavaCodeReferenceElement ref : refs) {
        assertNotNull(ref.resolve());
      }
    }))
      .setup(() -> EdtTestUtil.runInEdtAndWait(fixture.getPsiManager()::dropPsiCaches))
      .warmupIterations(100)
      .attempts(500)
      .start();
    // attempt.min.ms varies ~7% (from experiments)
  }

  private void ensureIndexUpToDate() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    ReadAction.runBlocking(
      () -> fixture.getJavaFacade().findClass(CommonClassNames.JAVA_UTIL_LIST, GlobalSearchScope.allScope(fixture.getProject())));
  }

  private void warmUpResolve() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    ReadAction.runBlocking(() -> {
      PsiJavaCodeReferenceElement ref =
        JavaPsiFacade.getElementFactory(fixture.getProject()).createReferenceFromText("java.util.List<String>", null);
      JavaResolveResult result = ref.advancedResolve(false);
      assertNotNull(result.getElement());
      assertEquals(1, result.getSubstitutor().getSubstitutionMap().size());
    });
  }

  @Test
  public void testStaticImportNetworkPerformance() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    RecursionManager.disableMissedCacheAssertions(fixture.getTestRootDisposable());
    warmUpResolve();

    VirtualFile dir = fixture.createTempVirtualDir();

    PsiReference ref = fixture.configureReferenceByFile("class/" + fixture.getTestName(false) + ".java", dir);
    int count = 15;

    StringBuilder imports = new StringBuilder();
    for (int i = 0; i < count; i++) {
      imports.append("import static Foo").append(i).append(".*;\n");
    }

    for (int i = 0; i < count; i++) {
      fixture.createFile(fixture.getModule(), dir, "Foo" + i + ".java", imports + "class Foo" + i + " extends Bar1, Bar2, Bar3 {}");
    }

    ensureIndexUpToDate();
    Benchmark.newBenchmark(fixture.getTestName(false), () -> ReadAction.runBlocking(() -> assertNull(ref.resolve())))
      .warmupIterations(20)
      .attempts(50)
      .start();
    // attempt.min.ms varies below the measurement threshold
  }

  @Test
  public void testLongIdentifierDotChain() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Benchmark.newBenchmark(fixture.getTestName(false), () -> ReadAction.runBlocking(() -> {
      PsiFile file = fixture.createDummyFile("a.java", "class C { { " + StringUtil.repeat("obj.", 100) + "foo } }");
      PsiReference ref = file.findReferenceAt(file.getText().indexOf("foo"));
      assertNull(ref.resolve());
    }))
      .warmupIterations(100)
      .attempts(300)
      .start();
    // attempt.min.ms varies below the measurement threshold
  }
}
