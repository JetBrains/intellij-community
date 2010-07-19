package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
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

  public void testImportsInsertedAlphabetically() throws Exception {
    CommandProcessor.getInstance().executeCommand(
      getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              @NonNls String text = "class I {}";
              PsiJavaFile file = (PsiJavaFile)configureByText(StdFileTypes.JAVA, text);
              assertEmpty(filter(doHighlighting(), HighlightSeverity.ERROR));

              checkAddImport(file, "java.util.List", "java.util.List");
              checkAddImport(file, "java.util.ArrayList", "java.util.ArrayList","java.util.List");
              checkAddImport(file, "java.util.HashMap", "java.util.ArrayList","java.util.HashMap","java.util.List");
              checkAddImport(file, "java.util.SortedMap", "java.util.ArrayList","java.util.HashMap","java.util.List","java.util.SortedMap");
              checkAddImport(file, "java.util.Map", "java.util.ArrayList","java.util.HashMap","java.util.List","java.util.Map","java.util.SortedMap");
              checkAddImport(file, "java.util.AbstractList", "java.util.AbstractList","java.util.ArrayList","java.util.HashMap","java.util.List","java.util.Map","java.util.SortedMap");
              checkAddImport(file, "java.util.AbstractList", "java.util.AbstractList","java.util.ArrayList","java.util.HashMap","java.util.List","java.util.Map","java.util.SortedMap");
              checkAddImport(file, "java.util.TreeMap", "java.util.AbstractList","java.util.ArrayList","java.util.HashMap","java.util.List","java.util.Map","java.util.SortedMap", "java.util.TreeMap");
              checkAddImport(file, "java.util.concurrent.atomic.AtomicBoolean", "java.util.AbstractList","java.util.ArrayList","java.util.HashMap","java.util.List","java.util.Map","java.util.SortedMap", "java.util.TreeMap", "java.util.concurrent.atomic.AtomicBoolean");
              checkAddImport(file, "java.io.File", "java.io.File","java.util.AbstractList","java.util.ArrayList","java.util.HashMap","java.util.List","java.util.Map","java.util.SortedMap", "java.util.TreeMap", "java.util.concurrent.atomic.AtomicBoolean");
            }
            catch (Throwable e) {
              LOG.error(e);
            }
          }
        });
      }
    }, "", "");
  }
  public void testStaticImportsGrouping() throws Exception {
    CommandProcessor.getInstance().executeCommand(
      getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
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

              PsiJavaFile file = (PsiJavaFile)configureByText(StdFileTypes.JAVA, text);
              assertEmpty(filter(doHighlighting(), HighlightSeverity.ERROR));

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

              assertOrder(file, "java.awt.*", "java.util.Map", "static java.lang.Math.max", "static java.lang.Math.min", "static javax.swing.SwingConstants.CENTER");

            }
            catch (Throwable e) {
              LOG.error(e);
            }
          }
        });
      }
    }, "", "");
  }

  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17("mock 1.5");
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
  public void testReimportConflictingClasses() throws Exception {
    configureByFile(BASE_PATH+"/x/Usage.java", BASE_PATH);
    assertEmpty(filter(doHighlighting(), HighlightSeverity.ERROR));

    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 2;
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings);
    try {
      JavaCodeStyleManager.getInstance(getProject()).optimizeImports(getFile());
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }


    @NonNls String fullPath = getTestDataPath() + BASE_PATH + "/x/Usage_afterOptimize.txt";
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fullPath.replace(File.separatorChar, '/'));
    String text = LoadTextUtil.loadText(vFile).toString();
    assertEquals(text, getFile().getText());
  }

  @DoNotWrapInCommand
  public void testAutoImportCaretLocation() throws Throwable {
    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    try {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      configureByText(StdFileTypes.JAVA, "class X { ArrayList<caret> c; }");
      ((UndoManagerImpl)UndoManagerImpl.getInstance(getProject())).flushCurrentCommandMerger();
      ((UndoManagerImpl)UndoManagerImpl.getInstance(getProject())).clearUndoRedoQueueInTests(getFile().getVirtualFile());
      type(" ");
      backspace();
      
      assertOneElement(filter(doHighlighting(), HighlightSeverity.ERROR));

      int offset = myEditor.getCaretModel().getOffset();
      PsiReference ref = myFile.findReferenceAt(offset - 1);
      assertTrue(ref instanceof PsiJavaCodeReferenceElement);

      ImportClassFixBase.Result result = new ImportClassFix((PsiJavaCodeReferenceElement)ref).doFix(getEditor(), true, false);
      assertEquals(ImportClassFixBase.Result.POPUP_NOT_SHOWN, result);
      UIUtil.dispatchAllInvocationEvents();

      myEditor.getCaretModel().moveToOffset(offset - 1);
      result = new ImportClassFix((PsiJavaCodeReferenceElement)ref).doFix(getEditor(), true, false);
      assertEquals(ImportClassFixBase.Result.CLASS_IMPORTED, result);
      UIUtil.dispatchAllInvocationEvents();

      assertEmpty(filter(doHighlighting(), HighlightSeverity.ERROR));
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  @DoNotWrapInCommand
  public void testAutoImportCaretLocation2() throws Throwable {
    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    try {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      configureByText(StdFileTypes.JAVA, "class X { <caret>ArrayList c = new ArrayList(); }");
      ((UndoManagerImpl)UndoManagerImpl.getInstance(getProject())).flushCurrentCommandMerger();
      ((UndoManagerImpl)UndoManagerImpl.getInstance(getProject())).clearUndoRedoQueueInTests(getFile().getVirtualFile());
      type(" ");
      backspace();
      
      assertEquals(2, filter(doHighlighting(), HighlightSeverity.ERROR).size());
      UIUtil.dispatchAllInvocationEvents();

      int offset = myEditor.getCaretModel().getOffset();
      PsiReference ref = myFile.findReferenceAt(offset);
      assertTrue(ref instanceof PsiJavaCodeReferenceElement);

      ImportClassFixBase.Result result = new ImportClassFix((PsiJavaCodeReferenceElement)ref).doFix(getEditor(), true, false);
      assertEquals(ImportClassFixBase.Result.CLASS_IMPORTED, result);
      UIUtil.dispatchAllInvocationEvents();

      assertEmpty(filter(doHighlighting(), HighlightSeverity.ERROR));
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  @CanChangeDocumentDuringHighlighting
  @DoNotWrapInCommand
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
      assertEquals(ImportClassFixBase.Result.CLASS_IMPORTED, result);

      assertNotSame(0, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements().length);
    }
    finally {
       CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  @CanChangeDocumentDuringHighlighting
  @DoNotWrapInCommand
  public void testAutoImportWorks() throws Throwable {
    @NonNls final String text = "class S { JFrame x; <caret> }";
    configureByText(StdFileTypes.JAVA, text);
    ((UndoManagerImpl)UndoManagerImpl.getInstance(getProject())).flushCurrentCommandMerger();
    ((UndoManagerImpl)UndoManagerImpl.getInstance(getProject())).clearUndoRedoQueueInTests(getFile().getVirtualFile());
    assertFalse(((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).canChangeFileSilently(getFile()));


    doHighlighting();
    assertFalse(((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).canChangeFileSilently(getFile()));

    type(" ");
    assertTrue(((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).canChangeFileSilently(getFile()));

    undo();

    assertFalse(((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).canChangeFileSilently(getFile()));//CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
  }

   @CanChangeDocumentDuringHighlighting
   @DoNotWrapInCommand
   public void testAutoOptimizeUnresolvedImports() throws Throwable {
     @NonNls String text = "import xxx.yyy; class S { } <caret> ";
     configureByText(StdFileTypes.JAVA, text);

     boolean old = CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY;
     CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = true;
     DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(true);

     try {
       List<HighlightInfo> errs = filter(doHighlighting(), HighlightSeverity.ERROR);

       assertEquals(1, errs.size());

       assertEquals(1, ((PsiJavaFile)getFile()).getImportList().getAllImportStatements().length);

       type("/* */");
       errs = filter(doHighlighting(), HighlightSeverity.ERROR);
       assertEquals(1, errs.size());

       UIUtil.dispatchAllInvocationEvents();

       assertEmpty(((PsiJavaFile)getFile()).getImportList().getAllImportStatements());
     }
     finally {
        CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = old;
     }
   }

}
