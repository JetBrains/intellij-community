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

import com.intellij.application.options.CodeStyle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.DaemonListeners;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.ui.UIUtil;
import com.siyeh.ig.naming.ClassNamingConvention;
import com.siyeh.ig.naming.NewClassNamingConventionInspection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class ImportHelperTest extends LightDaemonAnalyzerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings.getInstance(getProject()).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 100;
    DaemonCodeAnalyzer.getInstance(getProject()).setUpdateByTimerEnabled(false);
    enableInspectionTool(new UnusedImportInspection());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LightCodeInsightFixtureTestCase.JAVA_1_7; // Java 8 mock does not have java.sql package used here
  }

  private static PsiJavaFile configureByText(String text) {
    configureFromFileText("dummy.java", text);
    assertTrue(myFile instanceof PsiJavaFile);
    return (PsiJavaFile)myFile;
  }

  @Override
  protected void runTest() throws Throwable {
    // Avoid starting inside command (as implemented in super-class)
    // because we need to operate on application undo queue
    doRunTest();
  }

  public void testImportsInsertedAlphabetically() {
    @Language("JAVA")
    @NonNls String text = "class I {}";
    final PsiJavaFile file = configureByText(text);
    assertEmpty(highlightErrors());
    CommandProcessor.getInstance().executeCommand(
      getProject(), () -> WriteCommandAction.runWriteCommandAction(null, () -> {
        try {
          checkAddImport(file, CommonClassNames.JAVA_UTIL_LIST, CommonClassNames.JAVA_UTIL_LIST);
          checkAddImport(file, "java.util.ArrayList", "java.util.ArrayList", CommonClassNames.JAVA_UTIL_LIST);
          checkAddImport(file, "java.util.HashMap", "java.util.ArrayList", "java.util.HashMap", CommonClassNames.JAVA_UTIL_LIST);
          checkAddImport(file, "java.util.SortedMap", "java.util.ArrayList", "java.util.HashMap", CommonClassNames.JAVA_UTIL_LIST,
                         "java.util.SortedMap");
          checkAddImport(file, CommonClassNames.JAVA_UTIL_MAP, "java.util.ArrayList", "java.util.HashMap",
                         CommonClassNames.JAVA_UTIL_LIST,
                         CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap");
          checkAddImport(file, "java.util.AbstractList", "java.util.AbstractList", "java.util.ArrayList", "java.util.HashMap",
                         CommonClassNames.JAVA_UTIL_LIST,
                         CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap");
          checkAddImport(file, "java.util.AbstractList", "java.util.AbstractList", "java.util.ArrayList", "java.util.HashMap",
                         CommonClassNames.JAVA_UTIL_LIST,
                         CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap");
          checkAddImport(file, "java.util.TreeMap", "java.util.AbstractList", "java.util.ArrayList", "java.util.HashMap",
                         CommonClassNames.JAVA_UTIL_LIST,
                         CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap", "java.util.TreeMap");
          checkAddImport(file, "java.util.concurrent.atomic.AtomicBoolean", "java.util.AbstractList", "java.util.ArrayList",
                         "java.util.HashMap",
                         CommonClassNames.JAVA_UTIL_LIST,
                         CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap", "java.util.TreeMap",
                         "java.util.concurrent.atomic.AtomicBoolean");
          checkAddImport(file, "java.io.File", "java.io.File", "java.util.AbstractList", "java.util.ArrayList", "java.util.HashMap",
                         CommonClassNames.JAVA_UTIL_LIST,
                         CommonClassNames.JAVA_UTIL_MAP, "java.util.SortedMap", "java.util.TreeMap",
                         "java.util.concurrent.atomic.AtomicBoolean");
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }), "", "");
  }

  public void testStaticImportsGrouping() {
    @Language("JAVA")
    @NonNls String text = "import static java.lang.Math.max;\n" +
                          "import java.util.Map;\n" +
                          "\n" +
                          "import static java.lang.Math.min;\n" +
                          "\n" +
                          "import java.awt.Component;\n" +
                          "\n" +
                          "\n" +
                          "\n" +
                          "import static javax.swing.SwingConstants.CENTER;\n" +
                          "/** @noinspection ALL*/ " +
                          "class I {{ max(0, 0); Map.class.hashCode(); min(0,0); Component.class.hashCode(); int i = CENTER; }}";

    final PsiJavaFile file = configureByText(text);
    assertEmpty(highlightErrors());
    CommandProcessor.getInstance().executeCommand(
      getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
        try {

          JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
          javaSettings.LAYOUT_STATIC_IMPORTS_SEPARATELY = true;
          PackageEntryTable table = new PackageEntryTable();
          table.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
          table.addEntry(PackageEntry.BLANK_LINE_ENTRY);
          table.addEntry(new PackageEntry(false, "javax", true));
          table.addEntry(new PackageEntry(false, "java", true));
          table.addEntry(PackageEntry.BLANK_LINE_ENTRY);
          table.addEntry(new PackageEntry(true, "java", true));
          table.addEntry(PackageEntry.BLANK_LINE_ENTRY);
          table.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);

          JavaCodeStyleSettings.getInstance(getProject()).IMPORT_LAYOUT_TABLE.copyFrom(table);
          JavaCodeStyleManager.getInstance(getProject()).optimizeImports(file);

          assertOrder(file, "java.awt.*", CommonClassNames.JAVA_UTIL_MAP, "static java.lang.Math.max", "static java.lang.Math.min",
                      "static javax.swing.SwingConstants.CENTER");
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }), "", "");
  }

  private static void checkAddImport(PsiJavaFile file, String fqn, String... expectedOrder) {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(file);
    ImportHelper importHelper = new ImportHelper(settings);

    PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(fqn, GlobalSearchScope.allScope(getProject()));
    boolean b = importHelper.addImport(file, psiClass);
    assertTrue(b);

    assertOrder(file, expectedOrder);
  }

  private static void assertOrder(PsiJavaFile file, @NonNls String... expectedOrder) {
    PsiImportStatementBase[] statements = file.getImportList().getAllImportStatements();

    assertEquals(expectedOrder.length, statements.length);
    for (int i = 0; i < statements.length; i++) {
      PsiImportStatementBase statement = statements[i];
      String text = StringUtil.trimEnd(StringUtil.trimStart(statement.getText(), "import "), ";");
      assertEquals(expectedOrder[i], text);
    }
  }

  public void testConflictingClassesFromCurrentPackage() {
    @Language("JAVA")
    String text = "package java.util; class X{ Date d;}";
    final PsiJavaFile file = configureByText(text);
    assertEmpty(highlightErrors());

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(file);
      ImportHelper importHelper = new ImportHelper(settings);

      PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass("java.sql.Date", GlobalSearchScope.allScope(getProject()));
      boolean b = importHelper.addImport(file, psiClass);
      assertFalse(b); // must fail;
    });
  }

  public void testAutoImportCaretLocation() {
    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    try {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      @Language("JAVA")
      String text = "class X { ArrayList<caret> c; }";
      configureByText(text);
      ((UndoManagerImpl)UndoManager.getInstance(getProject())).flushCurrentCommandMerger();
      ((UndoManagerImpl)UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(getFile().getVirtualFile());
      type(" ");
      backspace();

      assertOneElement(highlightErrors());

      int offset = myEditor.getCaretModel().getOffset();
      PsiReference ref = myFile.findReferenceAt(offset - 1);
      assertTrue(ref instanceof PsiJavaCodeReferenceElement);

      ImportClassFixBase.Result result = new ImportClassFix((PsiJavaCodeReferenceElement)ref).doFix(getEditor(), true, false);
      assertEquals(ImportClassFixBase.Result.POPUP_NOT_SHOWN, result);
      UIUtil.dispatchAllInvocationEvents();

      myEditor.getCaretModel().moveToOffset(offset - 1);
      result = new ImportClassFix((PsiJavaCodeReferenceElement)ref).doFix(getEditor(), true, false);
      assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);
      UIUtil.dispatchAllInvocationEvents();

      assertEmpty(highlightErrors());
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  public void testAutoImportCaretLocation2() {
    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    try {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      @Language("JAVA")
      String text = "class X { <caret>ArrayList c = new ArrayList(); }";
      configureByText(text);
      ((UndoManagerImpl)UndoManager.getInstance(getProject())).flushCurrentCommandMerger();
      ((UndoManagerImpl)UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(getFile().getVirtualFile());
      type(" ");
      backspace();

      assertEquals(2, highlightErrors().size());
      UIUtil.dispatchAllInvocationEvents();

      int offset = myEditor.getCaretModel().getOffset();
      PsiReference ref = myFile.findReferenceAt(offset);
      assertTrue(ref instanceof PsiJavaCodeReferenceElement);

      ImportClassFixBase.Result result = new ImportClassFix((PsiJavaCodeReferenceElement)ref).doFix(getEditor(), true, false);
      assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);
      UIUtil.dispatchAllInvocationEvents();

      assertEmpty(highlightErrors());
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  public void testAutoImportWorksWhenITypeSpaceAfterClassName() {
    @Language("JAVA")
    @NonNls String text = "class S { ArrayList<caret> }";
    configureByText(text);

    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    try {
      doHighlighting();
      //caret is too close
      assertEmpty(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());

      type(" ");

      PsiJavaCodeReferenceElement element =
        (PsiJavaCodeReferenceElement)getFile().findReferenceAt(getEditor().getCaretModel().getOffset() - 2);
      ImportClassFix fix = new ImportClassFix(element);
      ImportClassFixBase.Result result = fix.doFix(getEditor(), false, false);
      assertEquals(ImportClassFixBase.Result.CLASS_AUTO_IMPORTED, result);

      assertNotSame(0, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements().length);
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  public void testAutoImportAfterUncomment() {
    @Language("JAVA")
    @NonNls String text = "class S { /*ArrayList l; HashMap h; <caret>*/ }";
    configureByText(text);

    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    try {
      doHighlighting();

      assertEmpty(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());

      EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_COMMENT_BLOCK);

      doHighlighting();
      UIUtil.dispatchAllInvocationEvents();

      assertEmpty(highlightErrors());

      assertNotSame(0, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements().length);
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  public void testEnsureOptimizeImportsWhenInspectionReportsErrors() {
    @Language("JAVA")
    @NonNls String text = "import java.util.List; class S { } <caret>";
    configureByText(text);
    //ensure error will be provided by a local inspection
    NewClassNamingConventionInspection tool = new NewClassNamingConventionInspection() {
      @NotNull
      @Override
      public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
      }

      @Nls
      @NotNull
      @Override
      public String getDisplayName() {
        return "Too short name";
      }

      @NotNull
      @Override
      public String getShortName() {
        return "TooShortName";
      }
    };
    tool.setEnabled(true, ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME);
    enableInspectionTool(tool);
    
    CodeInsightWorkspaceSettings.getInstance(getProject()).setOptimizeImportsOnTheFly(true, getTestRootDisposable());
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    List<HighlightInfo> errs = highlightErrors();
    //error corresponding to too short class name
    assertEquals(1, errs.size());

    assertEquals(1, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements().length);

    type("/* */");
    doHighlighting();
    UIUtil.dispatchAllInvocationEvents();
    assertEmpty(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
  }

  public void testAutoImportWorks() {
    @Language("JAVA")
    @NonNls final String text = "class S { JFrame x; <caret> }";
    configureByText(text);
    UndoManagerImpl undoManager = (UndoManagerImpl)UndoManager.getInstance(getProject());
    undoManager.flushCurrentCommandMerger();
    undoManager.clearUndoRedoQueueInTests(getFile().getVirtualFile());
    assertFalse(DaemonListeners.canChangeFileSilently(getFile()));


    doHighlighting();
    assertFalse(DaemonListeners.canChangeFileSilently(getFile()));

    type(" ");
    assertTrue(DaemonListeners.canChangeFileSilently(getFile()));

    undoManager.undo(TextEditorProvider.getInstance().getTextEditor(getEditor()));

    assertFalse(
      DaemonListeners.canChangeFileSilently(getFile()));//CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
  }


  public void testAutoImportOfGenericReference() {
    @Language("JAVA")
    @NonNls final String text = "class S {{ new ArrayList<caret><String> }}";
    configureByText(text);
    EditorTestUtil.setEditorVisibleSize(myEditor, 1000, 1000); // make sure editor is visible - auto-import works only for visible area
    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    try {
      DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

      ((UndoManagerImpl)UndoManager.getInstance(getProject())).flushCurrentCommandMerger();
      ((UndoManagerImpl)UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(getFile().getVirtualFile());
      type(" ");
      backspace();

      doHighlighting();
      //caret is too close
      assertEmpty(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());

      caretRight();

      doHighlighting();

      assertNotSame(0, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements().length);
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  public void testAutoOptimizeUnresolvedImports() {
    @Language("JAVA")
    @NonNls String text = "import xxx.yyy; class S { } <caret> ";
    configureByText(text);

    CodeInsightWorkspaceSettings.getInstance(getProject()).setOptimizeImportsOnTheFly(true, getTestRootDisposable());
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    List<HighlightInfo> errs = highlightErrors();

    //error in import list
    assertEquals(1, errs.size());

    assertEquals(1, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements().length);

    type("/* */");
    doHighlighting();
    UIUtil.dispatchAllInvocationEvents();

    assertEmpty(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
  }

  public void testAutoOptimizeDoesntSuddenlyRemoveImportsDuringTyping() {
    @Language("JAVA")
    @NonNls String text = "package x; " +
                          "import java.util.ArrayList; " +
                          "class S {{ <caret> ArrayList l;\n" +
                          "}}";
    configureByText(text);

    CodeInsightWorkspaceSettings.getInstance(getProject()).setOptimizeImportsOnTheFly(true, getTestRootDisposable());
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    List<HighlightInfo> errs = highlightErrors();

    assertEmpty(errs);

    type("/* ");
    UIUtil.dispatchAllInvocationEvents();
    errs = highlightErrors();
    assertNotEmpty(errs);
    PsiImportStatementBase imp = assertOneElement(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
    assertEquals("java.util.ArrayList", imp.getImportReference().getQualifiedName());
    UIUtil.dispatchAllInvocationEvents();

    type(" */ ");
    UIUtil.dispatchAllInvocationEvents();
    errs = highlightErrors();
    assertEmpty(errs);
    UIUtil.dispatchAllInvocationEvents();

    imp = assertOneElement(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
    assertEquals("java.util.ArrayList", imp.getImportReference().getQualifiedName());
  }

  public void testAutoInsertImportForInnerClass() {
    @Language("JAVA")
    @NonNls String text = "package x; class S { void f(ReadLock r){} } <caret> ";
    configureByText(text);

    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    try {
      List<HighlightInfo> errs = highlightErrors();
      assertEquals(1, errs.size());

      assertEmpty(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
      type("/* */");
      doHighlighting();
      UIUtil.dispatchAllInvocationEvents();
      assertEmpty(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  public void testAutoInsertImportForInnerClassAllowInnerClassImports() {
    @Language("JAVA")
    @NonNls String text = "package x; class S { void f(ReadLock r){} } <caret> ";
    configureByText(text);

    JavaCodeStyleSettings javaCodeStyleSettings = CodeStyle.getSettings(getFile()).getCustomSettings(JavaCodeStyleSettings.class);
    javaCodeStyleSettings.INSERT_INNER_CLASS_IMPORTS = true;
    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    try {
      List<HighlightInfo> errs = highlightErrors();
      assertEmpty(errs);

      assertSize(1, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  public void testAutoImportSkipsClassReferenceInMethodPosition() {
    @Language("JAVA")
    @NonNls String text =
      "package x; import java.util.HashMap; class S { HashMap<String,String> f(){ return  Hash<caret>Map <String, String >();} }  ";
    configureByText(text);

    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    try {
      List<HighlightInfo> errs = highlightErrors();
      assertTrue(errs.size() > 1);

      PsiJavaFile javaFile = (PsiJavaFile)getFile();
      assertEquals(1, javaFile.getImportList().getAllImportStatements().length);

      PsiReference ref = javaFile.findReferenceAt(getEditor().getCaretModel().getOffset());
      ImportClassFix fix = new ImportClassFix((PsiJavaCodeReferenceElement)ref);
      assertFalse(fix.isAvailable(getProject(), getEditor(), getFile()));
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  public void testAutoImportDoNotBreakCode() {
    @Language("JAVA")
    @NonNls String text = "package x; class S {{ S.<caret>\n Runnable r; }}";
    configureByText(text);

    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    CodeInsightWorkspaceSettings.getInstance(getProject()).setOptimizeImportsOnTheFly(true, getTestRootDisposable());
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    try {
      List<HighlightInfo> errs = highlightErrors();
      assertEquals(1, errs.size());
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  public void testAutoImportIgnoresUnresolvedImportReferences() {
    @Language("JAVA")
    @NonNls String text = "package x; import xxx.yyy.ArrayList; /** @noinspection ClassInitializerMayBeStatic*/ class S {{ ArrayList<caret> r; }}";
    configureByText(text);

    PsiJavaFile javaFile = (PsiJavaFile)getFile();
    PsiReference ref = javaFile.findReferenceAt(getEditor().getCaretModel().getOffset() - 1);
    ImportClassFix fix = new ImportClassFix((PsiJavaCodeReferenceElement)ref);
    //explicitly available
    assertTrue(fix.isAvailable(getProject(), getEditor(), getFile()));
    //hint is not available
    assertFalse(fix.showHint(getEditor()));
  }
}
