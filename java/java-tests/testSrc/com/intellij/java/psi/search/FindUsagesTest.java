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
package com.intellij.java.psi.search;

import com.intellij.JavaTestUtil;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.search.PsiReferenceProcessorAdapter;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FindUsagesTest extends PsiTestCase{
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/findUsages/" + getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
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
    final IntArrayList startsList = new IntArrayList();
    final IntArrayList endsList = new IntArrayList();
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

  private static void addReference(PsiReference ref, ArrayList<PsiFile> filesList, IntArrayList startsList, IntArrayList endsList) {
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

    final PsiFile nonCodeUsage = PsiFileFactory.getInstance(myProject).createFileFromText("a.xml", StdFileTypes.XML, "<root action='com.Foo'/>", 0, true);
    assertTrue(new UsageInfo(nonCodeUsage, 14, 21, true).getNavigationOffset() > 0);
  }

  public void testNonCodeClassUsages() throws Exception {
    final TempDirTestFixture tdf = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    tdf.setUp();

    try {
      new WriteCommandAction(getProject()) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          final ModifiableModuleModel moduleModel = ModuleManager.getInstance(getProject()).getModifiableModel();
          moduleModel.newModule("independent/independent.iml", StdModuleTypes.JAVA.getId());
          moduleModel.commit();

          tdf.createFile("plugin.xml", "<document>\n" +
                                       "  <action class=\"com.Foo\" />\n" +
                                       "  <action class=\"com.Foo.Bar\" />\n" +
                                       "  <action class=\"com.Foo$Bar\" />\n" +
                                       "</document>");

          PsiTestUtil.addContentRoot(ModuleManager.getInstance(getProject()).findModuleByName("independent"), tdf.getFile(""));
        }
      }.execute();

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

  public static void doTest(PsiElement element, String[] fileNames, int[] starts, int[] ends) {
    final ArrayList<PsiFile> filesList = new ArrayList<>();
    final IntArrayList startsList = new IntArrayList();
    final IntArrayList endsList = new IntArrayList();
    ReferencesSearch.search(element, GlobalSearchScope.projectScope(element.getProject()), false).forEach(new PsiReferenceProcessorAdapter(new PsiReferenceProcessor() {
        @Override
        public boolean execute(PsiReference ref) {
          addReference(ref, filesList, startsList, endsList);
          return true;
        }
      }));

    checkResult(fileNames, filesList, starts, startsList, ends, endsList);

  }

  private static class SearchResult implements Comparable<SearchResult> {
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

    public String toString() {
      return fileName + "[" + startOffset + ":" + endOffset + "]";
    }

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

  private static void checkResult(String[] fileNames, final ArrayList<PsiFile> filesList, int[] starts, final IntArrayList startsList, int[] ends, final IntArrayList endsList) {
    List<SearchResult> expected = new ArrayList<>();
    for (int i = 0; i < fileNames.length; i++) {
      String fileName = fileNames[i];
      expected.add(new SearchResult(fileName, i < starts.length ? starts[i] : -1, i < ends.length ? ends[i] : -1));
    }

    List<SearchResult> actual = new ArrayList<>();
    for (int i = 0; i < filesList.size(); i++) {
      PsiFile psiFile = filesList.get(i);
      actual.add(
        new SearchResult(psiFile.getName(), i < starts.length ? startsList.get(i) : -1, i < ends.length ? endsList.get(i) : -1));
    }

    Collections.sort(expected);
    Collections.sort(actual);

    assertEquals("Usages don't match", expected, actual);
  }
}
