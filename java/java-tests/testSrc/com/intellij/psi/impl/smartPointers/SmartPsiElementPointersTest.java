/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi.impl.smartPointers;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.*;
import com.intellij.util.FileContentUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@PlatformTestCase.WrapInCommand
@SkipSlowTestLocally
public class SmartPsiElementPointersTest extends CodeInsightTestCase {
  private VirtualFile myRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath() + "/codeEditor/smartPsiElementPointers";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    myRoot = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
  }

  public void testChangeInDocument() {
    PsiClass aClass = myJavaFacade.findClass("AClass", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = createPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    insertString(document, offset, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  private static void insertString(Document document, int offset, String s) {
    ApplicationManager.getApplication().runWriteAction(() -> document.insertString(offset, s));
  }

  // This test is unfair. If pointer would be asked for getElement() between commits it'll never restore again anyway.
  //
  public void testChangeInDocumentTwice() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = createPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    insertString(document, offset, "/*");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    insertString(document, offset + 2, "*/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testGetElementWhenDocumentModified() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = createPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    insertString(document, offset, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    insertString(document, offset, "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testKeepBeltWhenDocumentModified() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = createPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    insertString(document, offset, "/******/");

    pointer.getElement();

    insertString(document, offset, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testRetrieveOnUncommittedDocument() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiClass aClass = myJavaFacade.findClass("AClass", GlobalSearchScope.allScope(getProject()));
      assertNotNull(aClass);

      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
      Document document = documentManager.getDocument(aClass.getContainingFile());
      document.insertString(0, "/******/");

      SmartPointerEx pointer = createPointer(aClass.getNameIdentifier());

      document.insertString(0, "/**/");
      documentManager.commitAllDocuments();

      PsiElement element = pointer.getElement();
      assertNotNull(element);
      assertTrue(element.getParent() instanceof PsiClass);
      assertTrue(element.isValid());
    });
  }

  public void testNoAstLoadingWithoutDocumentChanges() {
    PsiClass aClass = myJavaFacade.findClass("Test",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);
    PsiFileImpl file = (PsiFileImpl)aClass.getContainingFile();

    createEditor(file.getVirtualFile());
    assertFalse(file.isContentsLoaded());

    SmartPointerEx pointer = createPointer(aClass);
    assertFalse(file.isContentsLoaded());

    //noinspection UnusedAssignment
    aClass = null;
    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNull(pointer.getCachedElement());

    assertNotNull(pointer.getElement());
    assertFalse(file.isContentsLoaded());
  }

  public void testTextFileClearingDoesNotCrash() {
    configureByText(PlainTextFileType.INSTANCE, "foo bar goo\n");
    SmartPsiElementPointer pointer = createPointer(myFile.getFirstChild());

    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertEquals(myFile.getFirstChild(), pointer.getElement());

    Document document = myFile.getViewProvider().getDocument();
    ApplicationManager.getApplication().runWriteAction(() -> document.deleteString(0, document.getTextLength()));


    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertEquals(myFile.getFirstChild(), pointer.getElement());

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertEquals(myFile.getFirstChild(), pointer.getElement());
  }

  public void testChangeInPsi() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = createPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    insertString(document, offset, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testPsiChangesWithLazyPointers() {
    PsiClass aClass = myJavaFacade.findClass("AClass", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    final SmartPsiElementPointer<PsiIdentifier> pointer =
      createPointer(aClass.getNameIdentifier());
    final PsiComment javadoc =
      JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createCommentFromText("/** javadoc */", aClass);
    ApplicationManager.getApplication().runWriteAction(() -> {
      aClass.getParent().addBefore(javadoc, aClass);
    });


    final PsiIdentifier elt = pointer.getElement();
    assertNotNull(elt);
    assertSame(elt, aClass.getNameIdentifier());
  }

  public void testTypePointer() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    final PsiTypeElement typeElement = myJavaFacade.findClass("Test",GlobalSearchScope.allScope(getProject())).getFields()[0].getTypeElement();

    SmartPsiElementPointer typePointer = createPointer(typeElement);
    SmartPsiElementPointer classPointer = createPointer(aClass);

    Document aClassDocument = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());
    Document testDocument = PsiDocumentManager.getInstance(myProject).getDocument(typeElement.getContainingFile());
    assertNotSame(aClassDocument, testDocument);

    insertString(aClassDocument, aClass.getTextOffset(), "/**/");
    insertString(testDocument, typeElement.getTextOffset(), "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = typePointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiTypeElement);
    assertTrue(element.isValid());
    assertEquals(classPointer.getElement(), PsiUtil.resolveClassInType(((PsiTypeElement)element).getType()));
  }

  public void testCreatePointerInBeforeDocumentChange() {
    final PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    final SmartPsiElementPointer[] pointer = new SmartPsiElementPointer[1];
    int offset = aClass.getTextOffset();
    DocumentListener listener = new DocumentListener() {
      @Override
      public void beforeDocumentChange(DocumentEvent event) {
        pointer[0] = createPointer(aClass);
      }
    };
    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    multicaster.addDocumentListener(listener);
    try {
      insertString(document, offset, "/******/");
    }
    finally {
      multicaster.removeDocumentListener(listener);
    }

    pointer[0].getElement();

    insertString(document, 0, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer[0].getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testCreatePointerWhenNoPsiFile() {
    myPsiManager.startBatchFilesProcessingMode(); // to use weak refs

    try {
      final PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
      assertNotNull(aClass);

      VirtualFile vFile = myRoot.findChild("AClass.java");
      assertNotNull(vFile);
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
      Document document = FileDocumentManager.getInstance().getDocument(vFile);

      final SmartPsiElementPointer pointer = createPointer(aClass);

      System.gc();
      /*
      PsiFile psiFile = myPsiManager.getFileManager().getCachedPsiFile(vFile);
      assertNull(psiFile);
      */

      insertString(document, 0, "class Foo{}\n");

      PsiElement element = pointer.getElement();
      assertEquals(aClass, element);

      insertString(document, 0, "/**/");
      psiDocumentManager.commitAllDocuments();

      if (aClass.isValid()) {
        aClass.getChildren();
      }

      element = pointer.getElement();
      assertNotNull(element);
      assertTrue(element instanceof PsiClass);
      assertTrue(element.isValid());
    }
    finally {
      myPsiManager.finishBatchFilesProcessingMode(); // to use weak refs
    }
  }

  public void testReplaceFile() {
    VirtualFile vfile = createChildData(myRoot, "X.java");
    setFileText(vfile, "public class X { public int X; }");

    PsiClass aClass = myJavaFacade.findClass("X", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);
    assertTrue(aClass.isValid());

    SmartPsiElementPointer classp = createPointer(aClass);
    SmartPsiElementPointer filep = createPointer(aClass.getContainingFile());

    FileContentUtil.reparseFiles(myProject, Collections.singleton(vfile), true);
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    assertFalse(aClass.isValid());

    PsiElement element = classp.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
    assertEquals(vfile, element.getContainingFile().getVirtualFile());

    element = filep.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiFile);
    assertTrue(element.isValid());
    assertEquals(vfile, element.getContainingFile().getVirtualFile());
  }

  public void testCreatePointerDoesNotLoadPsiTree() {
    VirtualFile vfile = createChildData(myRoot, "X.java");
    setFileText(vfile, "public class X { public int X; }");

    PsiClass aClass = myJavaFacade.findClass("X", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);
    assertTrue(aClass.isValid());

    PsiFileImpl file = (PsiFileImpl)aClass.getContainingFile();

    assertTreeLoaded(file, false);

    SmartPsiElementPointer p = createPointer(aClass);
    assertNotNull(p);

    assertTreeLoaded(file, false);

    assertInstanceOf(p.getElement(), PsiClass.class);

    assertTreeLoaded(file, false);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(file);
    insertString(document, 0, "/** asdasd */");
    documentManager.commitAllDocuments();

    // loaded tree
    assertTreeLoaded(file, true);

    assertInstanceOf(p.getElement(), PsiClass.class);

    assertTreeLoaded(file, true);
  }

  private static void assertTreeLoaded(PsiFileImpl file, boolean loaded) {
    FileElement treeElement = file.getTreeElement();
    assertEquals(loaded, treeElement != null);
    StubTree stubTree = file.getStubTree();
    assertEquals(loaded, stubTree == null);
  }

  public void testPointerDisambiguationAfterDupLine() {
    PsiJavaFile file = (PsiJavaFile)configureByText(StdFileTypes.JAVA, "class XXX{ void foo() { \n" +
                                       " <caret>foo();\n" +
                                       "}}");
    PsiClass aClass = file.getClasses()[0];
    assertNotNull(aClass);

    PsiReferenceExpression ref1 = PsiTreeUtil.getParentOfType(PsiUtilBase.getElementAtCaret(getEditor()), PsiReferenceExpression.class);
    SmartPsiElementPointer pointer1 = createPointer(ref1);

    ctrlD();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    Set<PsiReferenceExpression> refs = new THashSet<>();
    int offset=0;
    while (true) {
      offset = getEditor().getDocument().getText().indexOf("foo();", offset+1);
      if (offset == -1) break;
      PsiReferenceExpression ref2 = PsiTreeUtil.getParentOfType(getFile().findElementAt(offset), PsiReferenceExpression.class);
      refs.add(ref2);
    }
    refs.remove(ref1);
    assertEquals(1, refs.size());
    PsiReferenceExpression ref2 = refs.iterator().next();
    assertNotSame(ref1, ref2);
    SmartPsiElementPointer pointer2 = createPointer(ref2);
    assertNotSame(pointer1, pointer2);

    PsiElement element1 = pointer1.getElement();
    PsiElement element2 = pointer2.getElement();

    assertNotNull(element1);
    assertNotNull(element2);
    assertNotSame(element1, element2);

    assertFalse(getPointerManager().pointToTheSameElement(pointer1, pointer2));
  }

  public void testPointersRefCount() {
    PsiFile file = configureByText(JavaFileType.INSTANCE, "class X{}");
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    SmartPointerManagerImpl smartPointerManager = getPointerManager();
    SmartPsiElementPointer pointer1 = createPointer(aClass);
    SmartPsiElementPointer pointer2 = createPointer(aClass);
    assertSame(pointer1, pointer2);

    assertNotNull(pointer1.getRange());

    smartPointerManager.removePointer(pointer2);
    assertNotNull(pointer1.getRange());

    smartPointerManager.removePointer(pointer1);
    assertNull(pointer1.getRange());
  }

  private SmartPointerManagerImpl getPointerManager() {
    return (SmartPointerManagerImpl)SmartPointerManager.getInstance(myProject);
  }

  public void testPointersRefCountSaturated() {
    PsiFile file = configureByText(JavaFileType.INSTANCE, "class X{}");
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    SmartPointerManagerImpl smartPointerManager = getPointerManager();
    SmartPsiElementPointerImpl pointer1 = (SmartPsiElementPointerImpl)createPointer(aClass);
    for (int i=0; i<1000; i++) {
      SmartPsiElementPointer<PsiClass> pointer2 = createPointer(aClass);
      assertSame(pointer1, pointer2);
    }

    assertNotNull(pointer1.getRange());
    assertEquals(Byte.MAX_VALUE, pointer1.incrementAndGetReferenceCount(0));

    for (int i=0; i<1100; i++) {
      smartPointerManager.removePointer(pointer1);
      Assert.assertNotNull(pointer1.getRange());
    }
  }

  public void testSmartPointerCreationDoesNotLoadDocument() {
    PsiPackage aPackage = myJavaFacade.findPackage("java.io");
    for (PsiClass aClass : aPackage.getClasses()) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
      PsiFile file = aClass.getContainingFile();
      Document document = documentManager.getCachedDocument(file);
      if (document == null) { //ignore already loaded documents
        createPointer(aClass);
        assertNull(documentManager.getCachedDocument(file));
        //System.out.println("file = " + file);
      }
      else {
        System.out.println("already loaded file = " + file);
      }
    }
  }

  public void testSmartPointersSurvivePsiFileUnload() {
    final VirtualFile vfile = createChildData(myRoot, "X.txt");
    String xxx = "xxx";
    String text = xxx + " " + xxx + " " + xxx;
    setFileText(vfile, text);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(vfile);
    assertTrue(String.valueOf(psiFile), psiFile instanceof PsiPlainTextFile);
    SmartPointerManagerImpl manager = getPointerManager();
    TextRange range1 = TextRange.from(text.indexOf(xxx), xxx.length());
    SmartPsiFileRange pointer1 = manager.createSmartPsiFileRangePointer(psiFile, range1);
    TextRange range2 = TextRange.from(text.lastIndexOf(xxx), xxx.length());
    SmartPsiFileRange pointer2 = manager.createSmartPsiFileRangePointer(psiFile, range2);
    assertNotNull(FileDocumentManager.getInstance().getCachedDocument(vfile));

    SoftReference<PsiFile> ref = new SoftReference<>(psiFile);
    psiFile = null;
    while (ref.get() != null) {
      PlatformTestUtil.tryGcSoftlyReachableObjects();
    }
    assertNull(FileDocumentManager.getInstance().getCachedDocument(vfile));
    assertEquals(pointer1.getRange(), range1);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> insertString(FileDocumentManager.getInstance().getDocument(vfile), 0, " "));
    assertEquals(range1.shiftRight(1), pointer1.getRange());
    assertEquals(range2.shiftRight(1), pointer2.getRange());
  }

  public void testInXml() {
    final PsiFile file = configureByText(HtmlFileType.INSTANCE,
                                         "<!doctype html>\n" +
                                         "<html>\n" +
                                         "    <fieldset></fieldset>\n" +
                                         "    <select></select>\n" +
                                         "\n" +
                                         "    <caret>\n" +
                                         "</html>"
    );

    final XmlTag fieldSet = PsiTreeUtil.getParentOfType(file.findElementAt(file.getText().indexOf("fieldset")), XmlTag.class);
    assertNotNull(fieldSet);
    assertEquals("fieldset", fieldSet.getName());

    final XmlTag select = PsiTreeUtil.getParentOfType(file.findElementAt(file.getText().indexOf("select")), XmlTag.class);
    assertNotNull(select);
    assertEquals("select", select.getName());

    final SmartPsiElementPointer<XmlTag> fieldSetPointer = createPointer(fieldSet);
    final SmartPsiElementPointer<XmlTag> selectPointer = createPointer(select);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> insertString(getEditor().getDocument(), getEditor().getCaretModel().getOffset(), "<a></a>"));

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    final XmlTag newFieldSet = fieldSetPointer.getElement();
    assertNotNull(newFieldSet);
    assertEquals("fieldset", newFieldSet.getName());

    final XmlTag newSelect = selectPointer.getElement();
    assertNotNull(newSelect);
    assertEquals("select", newSelect.getName());
  }

  public void testInXml2() {
    final PsiFile file = configureByText(XmlFileType.INSTANCE,
                                         "<html>\n" +
                                         "    <ul class=\"dropdown-menu\">\n" +
                                         "        <li><a href=\"#\">One more separated link</a></li>\n" +
                                         "    </ul>\n" +
                                         "<caret>\n" +
                                         "</html>"
    );

    final XmlTag ul = PsiTreeUtil.getParentOfType(file.findElementAt(file.getText().indexOf("ul")), XmlTag.class);
    assertNotNull(ul);
    assertEquals("ul", ul.getName());
    assertEquals("dropdown-menu", ul.getAttributeValue("class"));

    SmartPsiElementPointer<XmlTag> ulPointer = createPointer(ul);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      int offset = getEditor().getCaretModel().getOffset();
      insertString(getEditor().getDocument(), offset, "    <ul class=\"nav navbar-nav navbar-right\">\n" +
                                                      "    </ul>\n");
    });

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    final XmlTag newUl = ulPointer.getElement();
    assertNotNull(newUl);
    assertEquals("ul", newUl.getName());
    assertEquals("dropdown-menu", newUl.getAttributeValue("class"));
  }

  public void testInsertImport() {
    final PsiFile file = configureByText(JavaFileType.INSTANCE,
                                         "class S {\n" +
                                         "}");

    PsiClass aClass = ((PsiJavaFile)file).getClasses()[0];

    WriteCommandAction.runWriteCommandAction(getProject(), () -> insertString(getEditor().getDocument(), 0, "import java.util.Map;\n"));

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    PsiClass aClass2 = ((PsiJavaFile)file).getClasses()[0];
    assertSame(aClass, aClass2);
  }

  public void testEqualPointerRangesWhenCreatedFromStubAndAST() {
    final PsiFile file = configureByText(JavaFileType.INSTANCE,
                                         "class S {\n" +
                                         "}");

    PsiClass aClass = ((PsiJavaFile)file).getClasses()[0];
    assertNotNull(((PsiFileImpl)file).getStubTree());

    final SmartPointerManager manager = getPointerManager();
    final SmartPsiElementPointer<PsiClass> pointer1 = createPointer(aClass);
    Segment range1 = pointer1.getRange();
    manager.removePointer(pointer1);

    final FileASTNode node = file.getNode();
    final SmartPsiElementPointer<PsiClass> pointer2 = createPointer(aClass);
    assertEquals(range1, pointer2.getRange());
    assertNotNull(node);
  }
  
  public void testEqualPointersWhenCreatedFromStubAndAST() {
    PsiJavaFile file = (PsiJavaFile)myJavaFacade.findClass("AClass", GlobalSearchScope.allScope(getProject())).getContainingFile();

    int hash1 = file.getClasses()[0].hashCode();
    final SmartPsiElementPointer<PsiClass> pointer1 = createPointer(file.getClasses()[0]);
    assertNotNull(((PsiFileImpl)file).getStubTree());
    
    PlatformTestUtil.tryGcSoftlyReachableObjects();

    final FileASTNode node = file.getNode();
    final SmartPsiElementPointer<PsiClass> pointer2 = createPointer(file.getClasses()[0]);
    assertFalse(hash1 == file.getClasses()[0].hashCode());
    assertEquals(pointer1, pointer2);
    assertEquals(pointer1.getRange(), pointer2.getRange());
    assertNotNull(node);
  }

  public void testLargeFileWithManyChangesPerformance() throws Exception {
    PsiFile file = createFile("a.txt", StringUtil.repeat("foo foo \n", 50000));
    final TextRange range = TextRange.from(10, 10);
    final SmartPsiFileRange pointer = getPointerManager().createSmartPsiFileRangePointer(file, range);

    final Document document = file.getViewProvider().getDocument();
    assertNotNull(document);

    WriteAction.run(() -> PlatformTestUtil.startPerformanceTest("smart pointer range update", 10000, () -> {
      document.setText(StringUtil.repeat("foo foo \n", 50000));
      for (int i = 0; i < 10000; i++) {
        document.insertString(i * 20 + 100, "x\n");
        assertFalse(PsiDocumentManager.getInstance(myProject).isCommitted(document));
        assertEquals(range, pointer.getRange());
      }
    }).assertTiming());

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    assertEquals(range, pointer.getRange());
  }

  public void testConvergingRanges() {
    configureByText(PlainTextFileType.INSTANCE, "aba");
    final Document document = myFile.getViewProvider().getDocument();
    assertNotNull(document);

    SmartPsiFileRange range1 = getPointerManager().createSmartPsiFileRangePointer(myFile, TextRange.create(0, 2));
    SmartPsiFileRange range2 = getPointerManager().createSmartPsiFileRangePointer(myFile, TextRange.create(1, 3));

    ApplicationManager.getApplication().runWriteAction(() -> {
      document.deleteString(0, 1);
      document.deleteString(1, 2);
    });

    assertEquals(TextRange.create(0, 1), range1.getRange());
    assertEquals(TextRange.create(0, 1), range2.getRange());

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    assertEquals(TextRange.create(0, 1), range1.getRange());
    assertEquals(TextRange.create(0, 1), range2.getRange());

    insertString(document, 0, "a");
    assertEquals(TextRange.create(1, 2), range1.getRange());
    assertEquals(TextRange.create(1, 2), range2.getRange());

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    assertEquals(TextRange.create(1, 2), range1.getRange());
    assertEquals(TextRange.create(1, 2), range2.getRange());
  }

  public void testMoveText() {
    PsiJavaFile file = (PsiJavaFile)configureByText(JavaFileType.INSTANCE, "class C1{}\nclass C2 {}");
    DocumentEx document = (DocumentEx)file.getViewProvider().getDocument();

    SmartPsiElementPointer<PsiClass> pointer1 =
      createPointer(file.getClasses()[0]);
    SmartPsiElementPointer<PsiClass> pointer2 =
      createPointer(file.getClasses()[1]);
    assertEquals("C1", pointer1.getElement().getName());
    assertEquals("C2", pointer2.getElement().getName());

    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNull(((SmartPointerEx) pointer1).getCachedElement());
    assertNull(((SmartPointerEx) pointer2).getCachedElement());

    TextRange range = file.getClasses()[1].getTextRange();
    ApplicationManager.getApplication().runWriteAction(() -> document.moveText(range.getStartOffset(), range.getEndOffset(), 0));

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    assertEquals("C1", pointer1.getElement().getName());
    assertEquals("C2", pointer2.getElement().getName());
  }

  public void testNonPhysicalFile() {
    PsiJavaFile file = (PsiJavaFile)myJavaFacade.findClass("AClass", GlobalSearchScope.allScope(getProject())).getContainingFile().copy();
    SmartPsiFileRange pointer = getPointerManager().createSmartPsiFileRangePointer(file, TextRange.create(1, 2));

    insertString(file.getViewProvider().getDocument(), 0, " ");

    assertEquals(TextRange.create(2, 3), pointer.getRange());
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    assertEquals(TextRange.create(2, 3), pointer.getRange());
  }
  
  public void testUpdateAfterInsertingIdenticalText() {
    PsiJavaFile file = (PsiJavaFile)configureByText(StdFileTypes.JAVA, "class Foo {\n" +
                                                                       "    void m() {\n" +
                                                                       "    }\n" +
                                                                       "<caret>}\n");
    PsiMethod method = file.getClasses()[0].getMethods()[0];
    TextRange originalRange = method.getTextRange();
    SmartPsiElementPointer pointer = createPointer(method);

    ApplicationManager.getApplication().runWriteAction(() -> EditorModificationUtil.insertStringAtCaret(myEditor, "    void m() {\n" +
                                                                                                                "    }\n"));

    PsiDocumentManager.getInstance(myProject).commitDocument(myEditor.getDocument());
    PsiElement element = pointer.getElement();
    assertNotNull(element);
    TextRange newRange = element.getTextRange();
    assertEquals(originalRange, newRange);
  }

  public void testAnchorInfoSurvivesPsiChange() {
    PsiJavaFile file = (PsiJavaFile)configureByText(JavaFileType.INSTANCE, "class C1{}\nclass C2 {}");

    SmartPsiElementPointer<PsiClass> pointer = createPointer(file.getClasses()[1]);
    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNull(((SmartPointerEx) pointer).getCachedElement());

    ApplicationManager.getApplication().runWriteAction(() -> file.getClasses()[0].delete());

    assertNotNull(pointer.getElement());
  }

  public void testPointerToEmptyElement() {
    PsiFile file = configureByText(JavaFileType.INSTANCE, "class Foo {\n" +
                                                             "  Test<String> test = new Test<>();\n" +
                                                             "}");
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(file, file.getText().indexOf("<>"), PsiJavaCodeReferenceElement.class, false);
    SmartPointerEx pointer = createPointer(ref.getParameterList().getTypeParameterElements()[0]);
    ref = null;

    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNull(pointer.getCachedElement());

    assertInstanceOf(pointer.getElement(), PsiTypeElement.class);
  }

  public void testPointerToEmptyElement2() {
    PsiFile file = configureByText(JavaFileType.INSTANCE, "class Foo {\n" +
                                                             "  void foo() {}\n" +
                                                             "}");
    PsiMethod method = PsiTreeUtil.findElementOfClassAtOffset(file, file.getText().indexOf("void"), PsiMethod.class, false);
    SmartPointerEx pointer1 = createPointer(method.getModifierList());
    SmartPointerEx pointer2 = createPointer(method.getTypeParameterList());
    method = null;

    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNull(pointer1.getCachedElement());
    assertNull(pointer2.getCachedElement());

    assertInstanceOf(pointer1.getElement(), PsiModifierList.class);
    assertInstanceOf(pointer2.getElement(), PsiTypeParameterList.class);
  }

  public void testPointerToReferenceSurvivesRename() {
    PsiFile file = configureByText(JavaFileType.INSTANCE, "class Foo extends Bar {}");
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(file, file.getText().indexOf("Bar"), PsiJavaCodeReferenceElement.class, false);
    SmartPointerEx pointer = createPointer(ref);
    ref = null;

    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNull(pointer.getCachedElement());

    ref = PsiTreeUtil.findElementOfClassAtOffset(file, file.getText().indexOf("Bar"), PsiJavaCodeReferenceElement.class, false);
    final PsiJavaCodeReferenceElement finalRef = ref;
    ApplicationManager.getApplication().runWriteAction(() -> {
      finalRef.handleElementRename("BarImpl");
    });

    assertNotNull(pointer.getElement());
  }

  public void testNonAnchoredStubbedElement() {
    PsiFile file = configureByText(JavaFileType.INSTANCE, "class Foo { { @NotNull String foo; } }");
    StubTree stubTree = ((PsiFileImpl)file).getStubTree();
    assertNotNull(stubTree);
    PsiElement anno = stubTree.getPlainList().stream().map(StubElement::getPsi).filter(psiElement -> psiElement instanceof PsiAnnotation).findFirst().get();

    SmartPsiElementPointer<PsiElement> pointer = createPointer(anno);
    assertNotNull(((PsiFileImpl)file).getStubTree());

    stubTree = null;
    anno = null;
    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNull(((SmartPointerEx) pointer).getCachedElement());

    insertString(file.getViewProvider().getDocument(), 0, " ");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    assertNotNull(pointer.getElement());
  }

  public void testManyPsiChangesWithManySmartPointersPerformance() throws Exception {
    String eachTag = "<a>\n" + StringUtil.repeat("   <a> </a>\n", 9) + "</a>\n";
    XmlFile file = (XmlFile)createFile("a.xml", "<root>\n" + StringUtil.repeat(eachTag, 500) + "</root>");
    List<XmlTag> tags = ContainerUtil.newArrayList(PsiTreeUtil.findChildrenOfType(file.getDocument(), XmlTag.class));
    List<SmartPsiElementPointer> pointers = tags.stream().map(this::createPointer).collect(Collectors.toList());
    ApplicationManager.getApplication().runWriteAction(() -> PlatformTestUtil.startPerformanceTest("smart pointer range update after PSI change", 21000, () -> {
      for (int i = 0; i < tags.size(); i++) {
        XmlTag tag = tags.get(i);
        SmartPsiElementPointer pointer = pointers.get(i);
        assertEquals(tag.getName().length(), TextRange.create(pointer.getRange()).getLength());
        assertEquals(tag.getName().length(), TextRange.create(pointer.getPsiRange()).getLength());

        tag.setName("bar1" + (i % 10));
        assertEquals(tag.getName().length(), TextRange.create(pointer.getRange()).getLength());
        assertEquals(tag.getName().length(), TextRange.create(pointer.getPsiRange()).getLength());
      }
      PostprocessReformattingAspect.getInstance(myProject).doPostponedFormatting();
    }).useLegacyScaling().assertTiming());
  }

  @NotNull
  private <T extends PsiElement> SmartPointerEx<T> createPointer(T element) {
    return (SmartPointerEx<T>)getPointerManager().createSmartPsiElementPointer(element);
  }

  public void testCommentingField() throws Exception {
    PsiJavaFile file = (PsiJavaFile)createFile("a.java", "class A {\n" +
                                                         "    int x;\n" +
                                                         "    int y;\n" +
                                                         "}");
    PsiField[] fields = file.getClasses()[0].getFields();
    SmartPointerEx<PsiField> pointer0 = createPointer(fields[0]);
    SmartPointerEx<PsiField> pointer1 = createPointer(fields[1]);

    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      Document document = file.getViewProvider().getDocument();
      assert document != null;
      document.insertString(file.getText().indexOf("int"), "//");
      commitDocument(document);
    });

    assertNull(pointer0.getElement());
    assertEquals("y", pointer1.getElement().getName());
  }

  public void testAnchorInfoHasRange() throws Exception {
    PsiJavaFile file = (PsiJavaFile)createFile("a.java", "class C1{}");
    assertNotNull(((PsiFileImpl) file).getStubTree());
    PsiClass psiClass = file.getClasses()[0];

    Segment range = createPointer(psiClass).getRange();
    assertNotNull(range);
    assertEquals(psiClass.getNameIdentifier().getTextRange(), TextRange.create(range));

    file = (PsiJavaFile)createFile("b.java", "class C2{}");
    assertNotNull(((PsiFileImpl) file).getStubTree());
    psiClass = file.getClasses()[0];

    range = createPointer(psiClass).getPsiRange();
    assertNotNull(range);
    assertEquals(psiClass.getNameIdentifier().getTextRange(), TextRange.create(range));
  }

  public void testManySmartPointersCreationDeletionPerformance() throws Exception {
    String text = StringUtil.repeatSymbol(' ', 100000);
    PsiFile file = createFile("a.txt", text);

    PlatformTestUtil.startPerformanceTest(getTestName(false), 500, () -> {
      List<SmartPsiFileRange> pointers = new ArrayList<>();
      for (int i = 0; i < text.length() - 1; i++) {
        pointers.add(getPointerManager().createSmartPsiFileRangePointer(file, new TextRange(i, i + 1)));
      }
      Collections.shuffle(pointers);
      for (SmartPsiFileRange pointer : pointers) {
        getPointerManager().removePointer(pointer);
      }
    }).assertTiming();
  }

  public void testDifferentHashCodesForDifferentElementsInOneFile() throws Exception {
    PsiClass clazz = ((PsiJavaFile)createFile("a.java", "class Foo { void foo(); }")).getClasses()[0];
    assertFalse(createPointer(clazz).hashCode() == createPointer(clazz.getMethods()[0]).hashCode());
  }

  public void testImportListPointerSurvivesImportAddition() throws Exception {
    PsiJavaFile file = (PsiJavaFile)createFile("a.java", "import foo.Bar;\nclass Foo {}");
    SmartPointerEx<PsiImportList> pointer = createPointer(file.getImportList());
    Document document = file.getViewProvider().getDocument();
    
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      document.insertString(document.getText().indexOf("class"), "import foo.Goo;\n");
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    });
    assertEquals(file.getImportList(), pointer.getElement());
    assertSize(2, file.getImportList().getImportStatements());

    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      document.insertString(0, " ");
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      document.deleteString(0, document.getText().indexOf("\nimport"));
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    });
    assertEquals(file.getImportList(), pointer.getElement());
    assertSize(1, file.getImportList().getImportStatements());
  }

  public void testNoAstLoadingOnFileRename() throws Exception {
    PsiFile psiFile = createFile("a.java", "class A {}");
    SmartPointerEx<PsiClass> pointer = createPointer(((PsiJavaFile)psiFile).getClasses()[0]);
    assertFalse(((PsiFileImpl)psiFile).isContentsLoaded());

    VirtualFile file = psiFile.getVirtualFile();
    WriteAction.run(() -> file.rename(this, "b.java"));

    assertTrue(psiFile.isValid());
    assertEquals(((PsiJavaFile)psiFile).getClasses()[0], pointer.getElement());
    
    assertFalse(((PsiFileImpl)psiFile).isContentsLoaded());
  }

  public void testDoubleRemoveIsAnError() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    SmartPointerEx<PsiFile> pointer = createPointer(createFile("a.java", "class A {}"));
    getPointerManager().removePointer(pointer);
    try {
      getPointerManager().removePointer(pointer);
      fail("Should have failed");
    }
    catch (AssertionError e) {
      assertTrue(e.getMessage(), e.getMessage().contains("Double smart pointer removal"));
    }
  }

  public void testStubSmartPointersAreCreatedEvenInAstPresence() throws Exception {
    PsiJavaFileImpl file = (PsiJavaFileImpl)createFile("a.java", "class A {}");
    assertNotNull(file.getNode());
    SmartPointerEx<PsiClass> pointer = createPointer(file.getClasses()[0]);
    
    PlatformTestUtil.tryGcSoftlyReachableObjects();
    assertNull(file.getTreeElement());

    assertNotNull(pointer.getElement());
    assertNull(file.getTreeElement());
  }

  public void testSurviveAfterWholeTextReplace() throws Exception {
    String text = "class A {" +
                  "void foo() {\n" +
                  "  //comment\n" +
                  "\n}" +
                  "\n" +
                  "void bar() {}\n" +
                  "void bar2() {}\n" +
                  "void bar3() {}\n" +
                  "void bar4() {}\n" +
                  "}";
    PsiJavaFileImpl file = (PsiJavaFileImpl)createFile("a.java", text);
    SmartPointerEx<PsiMethod> pointer = createPointer(file.getClasses()[0].getMethods()[1]);

    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      file.getViewProvider().getDocument().setText(text.replace("//comment", "//a\n//multiline\n// comment"));
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    });

    assertEquals(file.getClasses()[0].getMethods()[1], pointer.getElement());
  }

  public void testRangePointerSurvivesLanguageChange() throws Exception {
    PsiFile file = createFile("a.java", "abc");
    SmartPsiFileRange pointer = getPointerManager().createSmartPsiFileRangePointer(file, TextRange.from(0, 1));
    assertInstanceOf(pointer.getElement(), PsiJavaFile.class);
    
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      Document document = file.getViewProvider().getDocument();
      file.setName("a.txt");
      document.insertString(0, "ddd");
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    });
    
    assertInstanceOf(pointer.getElement(), PsiPlainTextFile.class);
    assertEquals(TextRange.from(3, 1), TextRange.create(pointer.getRange()));
  }

  public void testFileRangeWithUnicode() throws Exception {
    PsiFile file = createFile("a.java", "\u042b");
    assertEquals(1, file.getTextLength());
    SmartPsiFileRange pointer = getPointerManager().createSmartPsiFileRangePointer(file, TextRange.from(0, file.getTextLength()));
    assertEquals(TextRange.from(0, 1), TextRange.create(pointer.getPsiRange()));
    assertEquals(TextRange.from(0, 1), TextRange.create(pointer.getRange()));

  }

  public void testSurviveOnDocumentChangeAround() throws Exception {
    PsiFile file = createFile("a.java", "class Foo {}");
    SmartPointerEx<PsiClass> pointer = createPointer(((PsiJavaFile)file).getClasses()[0]);
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      file.getViewProvider().getDocument().setText(" class Foo {} ");
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    });
    assertNotNull(pointer.getElement());
  }

  // if the assertion is to be removed, please ensure that the test in IDEA-182567 passes
  public void testCreatingPointerInsidePsiListenerProhibited() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    PsiFile file = createFile("a.java", "class Foo { { int a; } }");

    try {
      WriteCommandAction.runWriteCommandAction(myProject, () -> {
        PsiLocalVariable var = PsiTreeUtil.findElementOfClassAtOffset(file, file.getText().indexOf("int"), PsiLocalVariable.class, false);
        PsiTreeChangeAdapter listener = new PsiTreeChangeAdapter() {
          @Override
          public void childAdded(@NotNull PsiTreeChangeEvent event) {
            createPointer(var);
          }
        };
        PsiManager.getInstance(getProject()).addPsiTreeChangeListener(listener);
        try {
          var.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        }
        finally {
          PsiManager.getInstance(getProject()).removePsiTreeChangeListener(listener);
        }
      });
      fail();
    }
    catch (AssertionError e) {
      assertTrue(e.getMessage(), e.getMessage().contains("shouldn't be created"));
    }
  }

  public void testCanRestoreErrorElementsAtSameOffset() throws Exception {
    PsiFile file = createFile("a.java", "class Foo { void foo() { if(IdeaLogger.ourErrorsOccurred != null) try(hrow IdeaLogger.ourErrorsOccurred; } }");
    for (PsiElement element : SyntaxTraverser.psiTraverser(file)) {
      SmartPointerEx<PsiElement> pointer = createPointer(element);
      assertEquals(element, pointer.getElement());
    }
  }

}
