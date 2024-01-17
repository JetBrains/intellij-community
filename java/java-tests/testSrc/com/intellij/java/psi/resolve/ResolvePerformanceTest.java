// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.resolve;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.testFramework.JavaResolveTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ResolvePerformanceTest extends JavaResolveTestCase {
  public void testPerformance1() throws Exception{
    final String fullPath = PathManagerEx.getTestDataPath() + "/psi/resolve/Thinlet.java";
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    final List<PsiReference> references = new ArrayList<>();
    assertNotNull("file " + fullPath + " not found", vFile);
    //final int[] ints = new int[10000000];
    System.gc();

    String fileText = StringUtil.convertLineSeparators(VfsUtilCore.loadText(vFile));
    myFile = createFile(vFile.getName(), fileText);
    myFile.accept(new JavaRecursiveElementWalkingVisitor(){
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression){
        references.add(expression);
        visitElement(expression);
      }
    });
    LOG.debug("");
    LOG.debug("Found " + references.size() + " references");
    long time = System.currentTimeMillis();

    // Not cached pass
    resolveAllReferences(references);
    LOG.debug("Not cached resolve: " + (System.currentTimeMillis() - time)/(double)1000 + "s");

    ApplicationManager.getApplication().runWriteAction(() -> ResolveCache.getInstance(myProject).clearCache(true));

    time = System.currentTimeMillis();
    // Cached pass
    resolveAllReferences(references);
    LOG.debug("Not cached resolve with cached repository: " + (System.currentTimeMillis() - time)/(double)1000 + "s");

    time = System.currentTimeMillis();
    // Cached pass
    resolveAllReferences(references);
    LOG.debug("Cached resolve: " + (System.currentTimeMillis() - time)/(double)1000 + "s");
 }

  public void testPerformance2() throws Exception{
    final String fullPath = PathManagerEx.getTestDataPath() + "/psi/resolve/ant/build.xml";
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    final List<PsiReference> references = new ArrayList<>();
    assertNotNull("file " + fullPath + " not found", vFile);

    String fileText = VfsUtilCore.loadText(vFile);
    fileText = StringUtil.convertLineSeparators(fileText);
    myFile = createFile(vFile.getName(), fileText);
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

    LOG.debug("");
    LOG.debug("Found " + references.size() + " references");

    resolveAllReferences(references);
  }

  private static void resolveAllReferences(List<PsiReference> list){
    for (PsiReference reference : list) {
      if (reference instanceof PsiJavaReference) {
        ((PsiJavaReference)reference).advancedResolve(false).isValidResult();
      }
      else {
        reference.resolve();
      }
    }
  }

  public void testStaticImportInTheSameClassPerformance() throws Exception {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    warmUpResolve();

    PsiReference ref = configureByFile("class/" + getTestName(false) + ".java");
    ensureIndexUpToDate();
    PlatformTestUtil.startPerformanceTest(getTestName(false), 150, () -> assertNull(ref.resolve()))
      .warmupIterations(20)
      .attempts(50)
      .assertTiming();
    // attempt.min.ms varies below the measurement threshold
  }

  public void testResolveOfManyStaticallyImportedFields() throws Exception {
    int fieldCount = 7000;

    createFile(myModule, "Constants.java",
               "interface Constants { " +
               IntStreamEx.range(0, fieldCount).mapToObj(i -> "String field" + i + ";").joining("\n") +
               "}");

    PsiFile file = createFile(myModule, "a.java",
                              "import static Constants.*;\n" +
                              "class Usage { \n" +
                              "void foo(String s) {}\n" +
                              "{" +
                              IntStreamEx.range(0, fieldCount).mapToObj(i -> "foo(field" + i + ");").joining("\n") +
                              "}}");

    List<PsiJavaCodeReferenceElement> refs = SyntaxTraverser.psiTraverser(file).filter(PsiJavaCodeReferenceElement.class).toList();

    PlatformTestUtil.startPerformanceTest(getTestName(false), 1_000, () -> {
      for (PsiJavaCodeReferenceElement ref : refs) {
        assertNotNull(ref.resolve());
      }
    })
      .setup(getPsiManager()::dropPsiCaches)
      .warmupIterations(100)
      .attempts(500)
      .assertTiming();
    // attempt.min.ms varies ~7% (from experiments)
  }

  private void ensureIndexUpToDate() {
    getJavaFacade().findClass(CommonClassNames.JAVA_UTIL_LIST, GlobalSearchScope.allScope(myProject));
  }

  private void warmUpResolve() {
    PsiJavaCodeReferenceElement ref = JavaPsiFacade.getElementFactory(myProject).createReferenceFromText("java.util.List<String>", null);
    JavaResolveResult result = ref.advancedResolve(false);
    assertNotNull(result.getElement());
    assertSize(1, result.getSubstitutor().getSubstitutionMap().keySet());
  }

  public void testStaticImportNetworkPerformance() throws Exception {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    warmUpResolve();

    VirtualFile dir = getTempDir().createVirtualDir();

    PsiReference ref = configureByFile("class/" + getTestName(false) + ".java", dir);
    int count = 15;

    StringBuilder imports = new StringBuilder();
    for (int i = 0; i < count; i++) {
      imports.append("import static Foo").append(i).append(".*;\n");
    }

    for (int i = 0; i < count; i++) {
      createFile(myModule, dir, "Foo" + i + ".java", imports + "class Foo" + i + " extends Bar1, Bar2, Bar3 {}");
    }

    ensureIndexUpToDate();
    PlatformTestUtil.startPerformanceTest(getTestName(false), 800, () -> assertNull(ref.resolve()))
      .warmupIterations(20)
      .attempts(50)
      .assertTiming();
    // attempt.min.ms varies below the measurement threshold
  }

  public void testLongIdentifierDotChain() {
    PlatformTestUtil.startPerformanceTest(getTestName(false), 800, () -> {
      PsiFile file = createDummyFile("a.java", "class C { { " + StringUtil.repeat("obj.", 100) + "foo } }");
      PsiReference ref = file.findReferenceAt(file.getText().indexOf("foo"));
      assertNull(ref.resolve());
    })
      .warmupIterations(100)
      .attempts(300)
      .assertTiming();
    // attempt.min.ms varies below the measurement threshold
  }
}
