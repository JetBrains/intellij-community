package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.codeInsight.generation.actions.CommentByBlockCommentAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.List;

@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
public class ImportHelperTest extends DaemonAnalyzerTestCase {
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UnusedImportLocalInspection()};
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 100;
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings);
    DaemonCodeAnalyzer.getInstance(getProject()).setUpdateByTimerEnabled(false);
  }

  @Override
  protected void tearDown() throws Exception {
    CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    super.tearDown();
  }

  @WrapInCommand
  public void testImportsInsertedAlphabetically() throws Throwable {
    @NonNls String text = "class I {}";
    final PsiJavaFile file = (PsiJavaFile)configureByText(StdFileTypes.JAVA, text);
    assertEmpty(highlightErrors());
    CommandProcessor.getInstance().executeCommand(
      getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              checkAddImport(file, CommonClassNames.JAVA_UTIL_LIST, CommonClassNames.JAVA_UTIL_LIST);
              checkAddImport(file, "java.util.ArrayList", "java.util.ArrayList", CommonClassNames.JAVA_UTIL_LIST);
              checkAddImport(file, "java.util.HashMap", "java.util.ArrayList","java.util.HashMap", CommonClassNames.JAVA_UTIL_LIST);
              checkAddImport(file, "java.util.SortedMap", "java.util.ArrayList","java.util.HashMap", CommonClassNames.JAVA_UTIL_LIST,"java.util.SortedMap");
              checkAddImport(file, CommonClassNames.JAVA_UTIL_MAP, "java.util.ArrayList","java.util.HashMap",
                             CommonClassNames.JAVA_UTIL_LIST,
                             CommonClassNames.JAVA_UTIL_MAP,"java.util.SortedMap");
              checkAddImport(file, "java.util.AbstractList", "java.util.AbstractList","java.util.ArrayList","java.util.HashMap",
                             CommonClassNames.JAVA_UTIL_LIST,
                             CommonClassNames.JAVA_UTIL_MAP,"java.util.SortedMap");
              checkAddImport(file, "java.util.AbstractList", "java.util.AbstractList","java.util.ArrayList","java.util.HashMap",
                             CommonClassNames.JAVA_UTIL_LIST,
                             CommonClassNames.JAVA_UTIL_MAP,"java.util.SortedMap");
              checkAddImport(file, "java.util.TreeMap", "java.util.AbstractList","java.util.ArrayList","java.util.HashMap",
                             CommonClassNames.JAVA_UTIL_LIST,
                             CommonClassNames.JAVA_UTIL_MAP,"java.util.SortedMap", "java.util.TreeMap");
              checkAddImport(file, "java.util.concurrent.atomic.AtomicBoolean", "java.util.AbstractList","java.util.ArrayList","java.util.HashMap",
                             CommonClassNames.JAVA_UTIL_LIST,
                             CommonClassNames.JAVA_UTIL_MAP,"java.util.SortedMap", "java.util.TreeMap", "java.util.concurrent.atomic.AtomicBoolean");
              checkAddImport(file, "java.io.File", "java.io.File","java.util.AbstractList","java.util.ArrayList","java.util.HashMap",
                             CommonClassNames.JAVA_UTIL_LIST,
                             CommonClassNames.JAVA_UTIL_MAP,"java.util.SortedMap", "java.util.TreeMap", "java.util.concurrent.atomic.AtomicBoolean");
            }
            catch (Throwable e) {
              LOG.error(e);
            }
          }
        });
      }
    }, "", "");
  }
  @WrapInCommand
  public void testStaticImportsGrouping() throws Throwable {
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
                          "class I {{ max(0, 0); Map.class.hashCode(); min(0,0); Component.class.hashCode(); int i = CENTER; }}";

    final PsiJavaFile file = (PsiJavaFile)configureByText(StdFileTypes.JAVA, text);
    assertEmpty(highlightErrors());
    CommandProcessor.getInstance().executeCommand(
      getProject(), new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {

                CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).clone();
                settings.LAYOUT_STATIC_IMPORTS_SEPARATELY = true;
                PackageEntryTable table = new PackageEntryTable();
                table.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
                table.addEntry(PackageEntry.BLANK_LINE_ENTRY);
                table.addEntry(new PackageEntry(false, "javax", true));
                table.addEntry(new PackageEntry(false, "java", true));
                table.addEntry(PackageEntry.BLANK_LINE_ENTRY);
                table.addEntry(new PackageEntry(true, "java", true));
                table.addEntry(PackageEntry.BLANK_LINE_ENTRY);
                table.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);

                settings.IMPORT_LAYOUT_TABLE.copyFrom(table);
                CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings);
                try {
                  JavaCodeStyleManager.getInstance(getProject()).optimizeImports(file);
                }
                finally {
                  CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
                }

                assertOrder(file, "java.awt.*", CommonClassNames.JAVA_UTIL_MAP, "static java.lang.Math.max", "static java.lang.Math.min", "static javax.swing.SwingConstants.CENTER");

              }
              catch (Throwable e) {
                LOG.error(e);
              }
            }
          });
        }
      }, "", "");
  }

  private void checkAddImport(PsiJavaFile file, String fqn, String... expectedOrder) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
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

  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting/reimportConflictingClasses";
  @WrapInCommand
  public void testReimportConflictingClasses() throws Exception {
    configureByFile(BASE_PATH+"/x/Usage.java", BASE_PATH);
    assertEmpty(highlightErrors());

    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 2;
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings);
    try {
      new WriteCommandAction.Simple(getProject()) {
        @Override
        protected void run() throws Throwable {
          JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile());
        }
      }.execute().throwException();
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }


    @NonNls String fullPath = getTestDataPath() + BASE_PATH + "/x/Usage_afterOptimize.txt";
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    String text = LoadTextUtil.loadText(vFile).toString();
    assertEquals(text, getFile().getText());
  }
  @WrapInCommand
  public void testConflictingClassesFromCurrentPackage() throws Throwable {
    final PsiFile file = configureByText(StdFileTypes.JAVA, "package java.util; class X{ Date d;}");
    assertEmpty(highlightErrors());

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
        ImportHelper importHelper = new ImportHelper(settings);

        PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass("java.sql.Date", GlobalSearchScope.allScope(getProject()));
        boolean b = importHelper.addImport((PsiJavaFile)file, psiClass);
        assertFalse(b); // must fail
      }
    }.execute().throwException();
  }

  public void testAutoImportCaretLocation() throws Throwable {
    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    try {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      configureByText(StdFileTypes.JAVA, "class X { ArrayList<caret> c; }");
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

  public void testAutoImportCaretLocation2() throws Throwable {
    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    try {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      configureByText(StdFileTypes.JAVA, "class X { <caret>ArrayList c = new ArrayList(); }");
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

  public void testAutoImportWorksWhenITypeSpaceAfterClassName() throws Throwable {
    @NonNls String text = "class S { ArrayList<caret> }";
    configureByText(StdFileTypes.JAVA, text);

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

  public void testAutoImportAfterUncomment() throws Throwable {
    @NonNls String text = "class S { /*ArrayList l; HashMap h; <caret>*/ }";
    configureByText(StdFileTypes.JAVA, text);

    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    try {
      doHighlighting();

      assertEmpty(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());

      CommentByBlockCommentAction action = new CommentByBlockCommentAction();
      action.actionPerformedImpl(getProject(), getEditor());

      assertEmpty(highlightErrors());

      assertNotSame(0, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements().length);
    }
    finally {
       CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  public void testAutoImportWorks() throws Throwable {
    @NonNls final String text = "class S { JFrame x; <caret> }";
    configureByText(StdFileTypes.JAVA, text);
    ((UndoManagerImpl)UndoManager.getInstance(getProject())).flushCurrentCommandMerger();
    ((UndoManagerImpl)UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(getFile().getVirtualFile());
    assertFalse(((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).canChangeFileSilently(getFile()));


    doHighlighting();
    assertFalse(((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).canChangeFileSilently(getFile()));

    type(" ");
    assertTrue(((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).canChangeFileSilently(getFile()));

    undo();

    assertFalse(((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).canChangeFileSilently(getFile()));//CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
  }


  public void testAutoImportOfGenericReference() throws Throwable {
    @NonNls final String text = "class S {{ new ArrayList<caret><> }}";
    configureByText(StdFileTypes.JAVA, text);
    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

    ((UndoManagerImpl)UndoManager.getInstance(getProject())).flushCurrentCommandMerger();
    ((UndoManagerImpl)UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(getFile().getVirtualFile());
    type(" ");
    backspace();

    try {
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

   public void testAutoOptimizeUnresolvedImports() throws Throwable {
     @NonNls String text = "import xxx.yyy; class S { } <caret> ";
     configureByText(StdFileTypes.JAVA, text);

     boolean old = CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY;
     CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = true;
     DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

     try {
       List<HighlightInfo> errs = highlightErrors();

       assertEquals(1, errs.size());

       assertEquals(1, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements().length);

       type("/* */");
       doHighlighting();
       UIUtil.dispatchAllInvocationEvents();

       assertEmpty(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
     }
     finally {
        CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = old;
     }
   }

   public void testAutoInsertImportForInnerClass() throws Throwable {
     @NonNls String text = "package x; class S { void f(ReadLock r){} } <caret> ";
     configureByText(StdFileTypes.JAVA, text);

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

   public void testAutoImportDoNotBreakCode() throws Throwable {
     @NonNls String text = "package x; class S {{ S.<caret>\n Runnable r; }}";
     configureByText(StdFileTypes.JAVA, text);

     boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
     boolean opt = CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY;
     CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
     CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = true;
     DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

     try {
       List<HighlightInfo> errs = highlightErrors();
       assertEquals(1, errs.size());
     }
     finally {
        CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
        CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = opt;
     }
   }

}
