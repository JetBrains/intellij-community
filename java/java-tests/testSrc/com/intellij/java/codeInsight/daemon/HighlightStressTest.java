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

package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@SkipSlowTestLocally
public class HighlightStressTest extends LightDaemonAnalyzerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if ("RandomEditingForUnused".equals(getTestName(false))) {
      enableInspectionTool(new UnusedDeclarationInspection());
      enableInspectionTool(new UnusedImportInspection());
    }
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    if ("RandomEditingForUnused".equals(getTestName(false))) {
      return LocalInspectionTool.EMPTY_ARRAY;
    }
    List<InspectionToolWrapper> all = InspectionToolRegistrar.getInstance().createTools();
    List<LocalInspectionTool> locals = new ArrayList<>();
    for (InspectionToolWrapper tool : all) {
      if (tool instanceof LocalInspectionToolWrapper) {
        LocalInspectionTool e = ((LocalInspectionToolWrapper)tool).getTool();
        locals.add(e);
      }
    }
    return locals.toArray(new LocalInspectionTool[locals.size()]);
  }

  @SuppressWarnings("All") @Language("JAVA")
  @NonNls private static final String text = "import java.util.*; class X { void f ( ) { \n"
  + "List < String > ls = new ArrayList < String > ( 1 ) ; ls . toString ( ) ; \n"
  + "List < Integer > is = new ArrayList < Integer > ( 1 ) ; is . toString ( ) ; \n"
  + "List i = new ArrayList ( 1 ) ; i . toString ( ) ; \n"
  + "Collection < Number > l2 = new ArrayList < Number > ( 10 ) ; l2 . toString ( ) ; \n"
  + "Collection < Number > l22 = new ArrayList < Number > ( ) ; l22 . toString ( ) ; \n"
  + "Map < Number , String > l3 = new HashMap < Number , String > ( 10 ) ; l3 . toString ( ) ; \n"
  + "Map < String , String > m = new HashMap < String , String > ( ) ; m . toString ( ) ; \n"
  + "Map < String , String > m1 = new HashMap < String , String > ( ) ; m1 . toString ( ) ; \n"
  + "Map < String , String > m2 = new HashMap < String , String > ( ) ; m2 . toString ( ) ; \n"
  + "Map < String , String > m3 = new HashMap < String , String > ( ) ; m3 . toString ( ) ; \n"
  + "Map < String , String > mi = new HashMap < String , String > ( 1 ) ; mi . toString ( ) ; \n"
  + "Map < String , String > mi1 = new HashMap < String , String > ( 1 ) ; mi1 . toString ( ) ; \n"
  + "Map < String , String > mi2 = new HashMap < String , String > ( 1 ) ; mi2 . toString ( ) ; \n"
  + "Map < String , String > mi3 = new HashMap < String , String > ( 1 ) ; mi3 . toString ( ) ; \n"
  + "Map < Number , String > l4 = new HashMap < Number , String > ( ) ; l4 . toString ( ) ; \n"
  + "Map < Number , String > l5 = new HashMap < Number , String > ( l4 ) ; l5 . toString ( ) ; \n"
  + "HashMap < Number , String > l6 = new HashMap < Number , String > ( ) ; l6 . toString ( ) ; \n"
  + "Map < List < Integer > , Map < String , List < String > > > l7 = new HashMap ( 1 ) ; l7 . toString ( ) ; \n"
  + "java . util . Map < java . util . List < Integer > , java . util . Map < String , java . util . List < String > > > l77 = \n" +
                                             "new java . util . HashMap ( 1 ) ; l77 . toString ( ) ; \n"
  + " } } ";

  public void testAllTheseConcurrentThreadsDoNotCrashAnything() {
    long time = System.currentTimeMillis();
    for (int i = 0; i < 20/*00000*/; i++) {
      //System.out.println("i = " + i);
      ((PsiManagerImpl)getPsiManager()).cleanupForNextTest();

      configureFromFileText("Stress.java", text);
      List<HighlightInfo> infos = doHighlighting();
      assertEmpty(DaemonAnalyzerTestCase.filter(infos, HighlightSeverity.ERROR));
      UIUtil.dispatchAllInvocationEvents();
      FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
    }
    LOG.debug(System.currentTimeMillis() - time+"ms");
  }

  public void _testHugeFile() {
    @NonNls String filePath =  "/psi/resolve/Thinlet.java";
    configureByFile(filePath);
    doHighlighting();

    int N = 42;
    long[] time = new long[N];
    for (int i = 0; i < N; i++) {
      DaemonCodeAnalyzer.getInstance(getProject()).restart();

      long start = System.currentTimeMillis();
      doHighlighting();
      long end = System.currentTimeMillis();
      time[i] = end - start;
      LOG.debug("i = " + i + "; time= "+(end-start));

      UIUtil.dispatchAllInvocationEvents();
    }
    LOG.debug("Average among the N/3 median times: " + ArrayUtil.averageAmongMedians(time, 3) + "ms");

    //System.out.println("JobLauncher.COUNT   = " + JobLauncher.COUNT);
    //System.out.println("JobLauncher.TINY    = " + JobLauncher.TINY_COUNT);
    //System.out.println("JobLauncher.LENGTH  = " + JobLauncher.LENGTH);
    //System.out.println("JobLauncher.ELAPSED = " + JobLauncher.ELAPSED);
    //System.out.println("Ave length : "+(JobLauncher.LENGTH.get()/1.0/JobLauncher.COUNT.get()));
    //System.out.println("Ave elapsed: "+(JobLauncher.ELAPSED.get()/1.0/JobLauncher.COUNT.get()));
    //
    //JobLauncher.lengths.sort();
    //System.out.println("Lengths: "+JobLauncher.lengths);
  }

  public void testRandomEditingPerformance() {
    configureFromFileText("Stress.java", text);
    List<HighlightInfo> oldWarnings = new ArrayList<>(doHighlighting());
    Comparator<HighlightInfo> infoComparator = (o1, o2) -> {
      if (o1.equals(o2)) return 0;
      if (o1.getActualStartOffset() != o2.getActualStartOffset()) return o1.getActualStartOffset() - o2.getActualStartOffset();
      return text(o1).compareTo(text(o2));
    };
    Collections.sort(oldWarnings, infoComparator);
    List<String> oldWarningTexts = new ArrayList<>();
    for (HighlightInfo info : oldWarnings) {
      oldWarningTexts.add(text(info));
    }

    Random random = new Random();

    DaemonCodeAnalyzer.getInstance(getProject()).restart();
    int N = 20;
    long[] time = new long[N];

    int oldWarningSize = oldWarnings.size();
    for (int i = 0; i < N; i++) {
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      long start = System.currentTimeMillis();

      LOG.debug("i = " + i);
      String s = myFile.getText();
      int offset;
      while (true) {
        offset = random.nextInt(s.length());
        if (s.charAt(offset) == ' ') break;
      }
      myEditor.getCaretModel().moveToOffset(offset);
      type("/*--*/");
      List<HighlightInfo> infos = doHighlighting();
      if (oldWarningSize != infos.size()) {
        infos = new ArrayList<>(infos);
        Collections.sort(infos, infoComparator);

        for (int k=0; k<Math.min(infos.size(), oldWarningSize);k++) {
          HighlightInfo info = infos.get(k);
          String text = text(info);
          String oldText = oldWarningTexts.get(k);
          if (!text.equals(oldText)) {
            System.err.println(k+"\n"+
                               "Old: "+oldText+"; info: " + oldWarnings.get(k)+";\n" +
                               "New: "+text+   "; info: " + info);
            break;
          }
        }
        assertEquals(infos.toString(), oldWarningSize, infos.size());
      }
      for (HighlightInfo info : infos) {
        assertNotSame(String.valueOf(info), HighlightSeverity.ERROR, info.getSeverity());
      }
      for (int k=0; k<"/*--*/".length();k++) {
        backspace();
      }
      UIUtil.dispatchAllInvocationEvents();

      long end = System.currentTimeMillis();
      time[i] = end - start;
    }
    FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();

    System.out.println("Average among the N/3 median times: " + ArrayUtil.averageAmongMedians(time, 3) + "ms");
  }

  @NotNull
  private static String text(@NotNull HighlightInfo info) {
    return info.getText() + info.getDescription();
  }

  public void testRandomEditingForUnused() {
    configureFromFileText("Stress.java", "class X {<caret>}");

    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(getProject());
    String[] names = cache.getAllClassNames();

    final StringBuilder imports = new StringBuilder();
    final StringBuilder usages = new StringBuilder();
    int v = 0;
    outer:
    for (String name : names) {
      PsiClass[] classes = cache.getClassesByName(name, GlobalSearchScope.allScope(getProject()));
      if (classes.length == 0) continue;
      PsiClass aClass = classes[0];
      if (!aClass.hasModifierProperty(PsiModifier.PUBLIC)) continue;
      if (aClass.getSuperClass() == null) continue;
      PsiClassType[] superTypes = aClass.getSuperTypes();
      if (superTypes.length == 0) continue;
      for (PsiClassType superType : superTypes) {
        PsiClass superClass = superType.resolve();
        if (superClass == null || !superClass.hasModifierProperty(PsiModifier.PUBLIC)) continue outer;
      }
      String qualifiedName = aClass.getQualifiedName();
      if (qualifiedName.startsWith("java.lang.invoke")) continue ; // java.lang.invoke.MethodHandle has weird access attributes in recent rt.jar which causes spurious highlighting errors
      if (!accessible(aClass, new THashSet<>())) continue;
      imports.append("import " + qualifiedName + ";\n");
      usages.append("/**/ "+aClass.getName() + " var" + v + " = null; var" + v + ".toString();\n");
      v++;
      if (v>100) break;
    }
    final String text = imports + "\n class X {{\n" + usages + "}}";
    WriteCommandAction.runWriteCommandAction(null, () -> getEditor().getDocument().setText(text));

    List<HighlightInfo> errors = DaemonAnalyzerTestCase.filter(doHighlighting(), HighlightSeverity.WARNING);
    assertEmpty(text, errors);
    Random random = new Random();
    int unused = 0;
    for (int i = 0; i < 100; i++) {
      String s = myFile.getText();

      int offset;
      while (true) {
        offset = random.nextInt(s.length());
        if (CharArrayUtil.regionMatches(s, offset, "/**/") || CharArrayUtil.regionMatches(s, offset, "//")) break;
      }

      char next = offset < s.length()-1 ? s.charAt(offset+1) : 0;
      if (next == '/') {
        myEditor.getCaretModel().moveToOffset(offset + 1);
        type("**");
        unused--;
      }
      else if (next == '*') {
        myEditor.getCaretModel().moveToOffset(offset + 1);
        delete();
        delete();
        unused++;
      }
      else {
        continue;
      }
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      getFile().accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          assertTrue(element.toString(), element.isValid());
          super.visitElement(element);
        }
      });

      //System.out.println("i = " + i + " " + next + " at "+offset);

      List<HighlightInfo> infos = doHighlighting();
      errors = DaemonAnalyzerTestCase.filter(infos, HighlightSeverity.ERROR);
      assertEmpty(errors);
      List<HighlightInfo> warns = DaemonAnalyzerTestCase.filter(infos, HighlightSeverity.WARNING);
      if (unused != warns.size()) {
        assertEquals(warns.toString(), unused, warns.size());
      }
    }
    FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
  }

  private static boolean accessible(PsiClass aClass, Set<PsiClass> visited) {
    if (!visited.add(aClass)) return false;
    // this class and all its super- and containing- classes should be public
    if (!aClass.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    for (PsiClass superClass : aClass.getSupers()) {
      if (!accessible(superClass, visited)) return false;
    }
    PsiClass containingClass = aClass.getContainingClass();
    return containingClass == null || accessible(containingClass, visited);
  }
}
