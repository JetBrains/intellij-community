/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl.smartPointers;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.*;
import com.intellij.util.FileContentUtil;
import gnu.trove.THashSet;
import org.junit.Assert;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Set;

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

    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    document.insertString(offset, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  // This test is unfair. If pointer would be asked for getElement() between commits it'll never restore again anyway.
  //
  public void testChangeInDocumentTwice() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    document.insertString(offset, "/*");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    document.insertString(offset + 2, "*/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testGetElementWhenDocumentModified() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    document.insertString(offset, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    document.insertString(offset, "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testKeepBeltWhenDocumentModified() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    document.insertString(offset, "/******/");

    pointer.getElement();

    document.insertString(offset, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testChangeInPsi() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());

    int offset = aClass.getTextOffset();
    document.insertString(offset, "/**/");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiElement element = pointer.getElement();
    assertNotNull(element);
    assertTrue(element instanceof PsiClass);
    assertTrue(element.isValid());
  }

  public void testPsiChangesWithLazyPointers() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("AClass", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);

    final SmartPsiElementPointer<PsiIdentifier> pointer =
      SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass.getNameIdentifier());
    final PsiComment javadoc =
      JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createCommentFromText("/** javadoc */", aClass);
    aClass.getParent().addBefore(javadoc, aClass);

    final PsiIdentifier elt = pointer.getElement();
    assertNotNull(elt);
    assertSame(elt, aClass.getNameIdentifier());
  }

  public void testTypePointer() {
    PsiClass aClass = myJavaFacade.findClass("AClass",GlobalSearchScope.allScope(getProject()));
    final PsiTypeElement typeElement = myJavaFacade.findClass("Test",GlobalSearchScope.allScope(getProject())).getFields()[0].getTypeElement();

    SmartPsiElementPointer typePointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(typeElement);
    SmartPsiElementPointer classPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);

    Document aClassDocument = PsiDocumentManager.getInstance(myProject).getDocument(aClass.getContainingFile());
    Document testDocument = PsiDocumentManager.getInstance(myProject).getDocument(typeElement.getContainingFile());
    assertNotSame(aClassDocument, testDocument);

    aClassDocument.insertString(aClass.getTextOffset(), "/**/");
    testDocument.insertString(typeElement.getTextOffset(), "/**/");
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
        pointer[0] = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
      }

      @Override
      public void documentChanged(DocumentEvent event) {
      }
    };
    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    multicaster.addDocumentListener(listener);
    try {
      document.insertString(offset, "/******/");
    }
    finally {
      multicaster.removeDocumentListener(listener);
    }

    pointer[0].getElement();

    document.insertString(0, "/**/");
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

      final SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);

      System.gc();
      /*
      PsiFile psiFile = myPsiManager.getFileManager().getCachedPsiFile(vFile);
      assertNull(psiFile);
      */

      document.insertString(0, "class Foo{}\n");

      PsiElement element = pointer.getElement();
      assertEquals(aClass, element);

      document.insertString(0, "/**/");
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

  public void testReplaceFile() throws IOException {
    VirtualFile vfile = myRoot.createChildData(this, "X.java");
    VfsUtil.saveText(vfile, "public class X { public int X; }");

    PsiClass aClass = myJavaFacade.findClass("X", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);
    assertTrue(aClass.isValid());

    SmartPsiElementPointer classp = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    SmartPsiElementPointer filep = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass.getContainingFile());

    FileContentUtil.reparseFiles(myProject, Collections.<VirtualFile>singleton(vfile), true);
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

  public void testCreatePointerDoesNotLoadPsiTree() throws IOException {
    VirtualFile vfile = myRoot.createChildData(this, "X.java");
    VfsUtil.saveText(vfile, "public class X { public int X; }");

    PsiClass aClass = myJavaFacade.findClass("X", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);
    assertTrue(aClass.isValid());

    PsiFileImpl file = (PsiFileImpl)aClass.getContainingFile();

    assertTreeLoaded(file, false);

    SmartPsiElementPointer p = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(aClass);
    assertNotNull(p);

    assertTreeLoaded(file, false);

    assertInstanceOf(p.getElement(), PsiClass.class);

    assertTreeLoaded(file, false);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(file);
    document.insertString(0, "/** asdasd */");
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

  public void testPointerDisambiguationAfterDupLine() throws Exception {
    PsiJavaFile file = (PsiJavaFile)configureByText(StdFileTypes.JAVA, "class XXX{ void foo() { \n" +
                                       " <caret>foo();\n" +
                                       "}}");
    PsiClass aClass = file.getClasses()[0];
    assertNotNull(aClass);

    PsiReferenceExpression ref1 = PsiTreeUtil.getParentOfType(PsiUtilBase.getElementAtCaret(getEditor()), PsiReferenceExpression.class);
    SmartPsiElementPointer pointer1 = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(ref1);

    ctrlD();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    Set<PsiReferenceExpression> refs = new THashSet<PsiReferenceExpression>();
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
    SmartPsiElementPointer pointer2 = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(ref2);
    assertNotSame(pointer1, pointer2);

    PsiElement element1 = pointer1.getElement();
    PsiElement element2 = pointer2.getElement();

    assertNotNull(element1);
    assertNotNull(element2);
    assertNotSame(element1, element2);

    assertFalse(SmartPointerManager.getInstance(myProject).pointToTheSameElement(pointer1, pointer2));
  }

  public void testPointersRefCount() throws Exception {
    PsiFile file = configureByText(JavaFileType.INSTANCE, "class X{}");
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    SmartPointerManagerImpl smartPointerManager = (SmartPointerManagerImpl)SmartPointerManager.getInstance(myProject);
    SmartPsiElementPointer pointer1 = smartPointerManager.createSmartPsiElementPointer(aClass);
    SmartPsiElementPointer pointer2 = smartPointerManager.createSmartPsiElementPointer(aClass);
    assertSame(pointer1, pointer2);

    assertNotNull(pointer1.getRange());

    boolean removed2 = smartPointerManager.removePointer(pointer2);
    assertFalse(removed2);
    assertNotNull(pointer1.getRange());

    boolean removed1 = smartPointerManager.removePointer(pointer1);
    assertTrue(removed1);
    assertNull(pointer1.getRange());
  }

  public void testPointersRefCountSaturated() throws Exception {
    PsiFile file = configureByText(JavaFileType.INSTANCE, "class X{}");
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    SmartPointerManagerImpl smartPointerManager = (SmartPointerManagerImpl)SmartPointerManager.getInstance(myProject);
    SmartPsiElementPointerImpl pointer1 = (SmartPsiElementPointerImpl)smartPointerManager.createSmartPsiElementPointer(aClass);
    for (int i=0; i<1000; i++) {
      SmartPsiElementPointer<PsiClass> pointer2 = smartPointerManager.createSmartPsiElementPointer(aClass);
      assertSame(pointer1, pointer2);
    }

    assertNotNull(pointer1.getRange());
    assertEquals(Byte.MAX_VALUE, pointer1.incrementAndGetReferenceCount(0));

    for (int i=0; i<1100; i++) {
      boolean removed1 = smartPointerManager.removePointer(pointer1);
      assertFalse(removed1);
      Assert.assertNotNull(pointer1.getRange());
    }
  }

  public void testSmartPointerCreationDoesNotLoadDocument() {
    PsiPackage aPackage = myJavaFacade.findPackage("java.io");
    SmartPointerManagerImpl smartPointerManager = (SmartPointerManagerImpl)SmartPointerManager.getInstance(myProject);
    for (PsiClass aClass : aPackage.getClasses()) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
      PsiFile file = aClass.getContainingFile();
      Document document = documentManager.getCachedDocument(file);
      if (document == null) { //ignore already loaded documents
        SmartPsiElementPointer pointer = smartPointerManager.createSmartPsiElementPointer(aClass);
        assertNull(documentManager.getCachedDocument(file));
        //System.out.println("file = " + file);
      }
      else {
        System.out.println("already loaded file = " + file);
      }
    }
  }

  public void testSmartPointersSurvivePsiFileUnload() throws IOException {
    final VirtualFile vfile = myRoot.createChildData(this, "X.txt");
    String xxx = "xxx";
    String text = xxx + " " + xxx + " " + xxx;
    VfsUtil.saveText(vfile, text);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(vfile);
    assertTrue(String.valueOf(psiFile), psiFile instanceof PsiPlainTextFile);
    SmartPointerManagerImpl manager = (SmartPointerManagerImpl)SmartPointerManager.getInstance(myProject);
    TextRange range1 = TextRange.from(text.indexOf(xxx), xxx.length());
    SmartPsiFileRange pointer1 = manager.createSmartPsiFileRangePointer(psiFile, range1);
    TextRange range2 = TextRange.from(text.lastIndexOf(xxx), xxx.length());
    SmartPsiFileRange pointer2 = manager.createSmartPsiFileRangePointer(psiFile, range2);
    assertNotNull(FileDocumentManager.getInstance().getCachedDocument(vfile));

    SoftReference<PsiFile> ref = new SoftReference<PsiFile>(psiFile);
    psiFile = null;
    while (ref.get() != null) {
      PlatformTestUtil.tryGcSoftlyReachableObjects();
    }
    assertNull(FileDocumentManager.getInstance().getCachedDocument(vfile));
    assertEquals(pointer1.getRange(), range1);
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        FileDocumentManager.getInstance().getDocument(vfile).insertString(0, " ");
      }
    });
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

    final SmartPsiElementPointer<XmlTag> fieldSetPointer = SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(
      fieldSet);
    final SmartPsiElementPointer<XmlTag> selectPointer = SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(select);

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        getEditor().getDocument().insertString(getEditor().getCaretModel().getOffset(), "<a></a>");
      }
    });

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    final XmlTag newFieldSet = fieldSetPointer.getElement();
    assertNotNull(newFieldSet);
    assertEquals("fieldset", newFieldSet.getName());

    final XmlTag newSelect = selectPointer.getElement();
    assertNotNull(newSelect);
    assertEquals("select", newSelect.getName());
  }

  public void _testInXml2() {
    final PsiFile file = configureByText(HtmlFileType.INSTANCE,
                                         "<!DOCTYPE html>\n" +
                                         "<html>\n" +
                                         "<head>\n" +
                                         "    <title></title>\n" +
                                         "</head>\n" +
                                         "<body>\n" +
                                         "<div class=\"cls\">\n" +
                                         "    <ul class=\"dropdown-menu\">\n" +
                                         "        <li><a href=\"#\">Action</a></li>\n" +
                                         "        <li><a href=\"#\">Another action</a></li>\n" +
                                         "        <li><a href=\"#\">Something else here</a></li>\n" +
                                         "        <li class=\"divider\"></li>\n" +
                                         "        <li class=\"dropdown-header\">Nav header</li>\n" +
                                         "        <li><a href=\"#\">Separated link</a></li>\n" +
                                         "        <li><a href=\"#\">One more separated link</a></li>\n" +
                                         "    </ul>\n" +
                                         "<caret>\n" +
                                         "</div>\n" +
                                         "</body>\n" +
                                         "</html>"
    );

    final XmlTag ul = PsiTreeUtil.getParentOfType(file.findElementAt(file.getText().indexOf("ul")), XmlTag.class);
    assertNotNull(ul);
    assertEquals("ul", ul.getName());
    assertEquals("dropdown-menu", ul.getAttributeValue("class"));

    final SmartPsiElementPointer<XmlTag> ulPointer = SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(
      ul);

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        getEditor().getDocument().insertString(getEditor().getCaretModel().getOffset(), "    <ul class=\"nav navbar-nav navbar-right\">\n" +
                                                                                        "        <li><a href=\"../navbar/\">Default</a></li>\n" +
                                                                                        "        <li class=\"active\"><a href=\"./\">Static top</a></li>\n" +
                                                                                        "        <li><a href=\"../navbar-fixed-top/\">Fixed top</a></li>\n" +
                                                                                        "    </ul>\n");
      }
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

    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        getEditor().getDocument().insertString(0, "import java.util.Map;\n");
      }
    });

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

    final SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
    final SmartPsiElementPointer<PsiClass> pointer1 = manager.createSmartPsiElementPointer(aClass);
    Segment range1 = pointer1.getRange();
    manager.removePointer(pointer1);

    final FileASTNode node = file.getNode();
    final SmartPsiElementPointer<PsiClass> pointer2 = manager.createSmartPsiElementPointer(aClass);
    assertEquals(range1, pointer2.getRange());
    assertNotNull(node);
  }
  
  public void testEqualPointersWhenCreatedFromStubAndAST() {
    final PsiFile file = configureByText(JavaFileType.INSTANCE,
                                         "class S {\n" +
                                         "}");


    final SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
    int hash1 = ((PsiJavaFile)file).getClasses()[0].hashCode();
    final SmartPsiElementPointer<PsiClass> pointer1 = manager.createSmartPsiElementPointer(((PsiJavaFile)file).getClasses()[0]);
    assertNotNull(((PsiFileImpl)file).getStubTree());
    
    PlatformTestUtil.tryGcSoftlyReachableObjects();

    final FileASTNode node = file.getNode();
    final SmartPsiElementPointer<PsiClass> pointer2 = manager.createSmartPsiElementPointer(((PsiJavaFile)file).getClasses()[0]);
    assertFalse(hash1 == ((PsiJavaFile)file).getClasses()[0].hashCode());
    assertEquals(pointer1, pointer2);
    assertEquals(pointer1.getRange(), pointer2.getRange());
    assertNotNull(node);
  }
}
