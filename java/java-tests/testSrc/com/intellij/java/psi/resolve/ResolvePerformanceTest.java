/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi.resolve;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ResolveTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ResolvePerformanceTest extends ResolveTestCase {
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
      @Override public void visitReferenceExpression(PsiReferenceExpression expression){
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
      @Override public void visitXmlAttributeValue(XmlAttributeValue value) {
        ContainerUtil.addAll(references, value.getReferences());
        super.visitXmlAttributeValue(value);
      }
    });

    myFile.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression){
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
    warmUpResolve();

    PsiReference ref = configureByFile("class/" + getTestName(false) + ".java");
    ensureIndexUpToDate();
    PlatformTestUtil.startPerformanceTest(getTestName(false), 90, () -> assertNull(ref.resolve()))
      .attempts(1).assertTiming();
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
    warmUpResolve();

    VirtualFile dir = createTempVfsDirectory();

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
      .attempts(1).assertTiming();
  }
}
