/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.mock.MockDocument;
import com.intellij.mock.MockPsiFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.TextBlock;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;

import java.io.File;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicInteger;

public class PsiDocumentManagerImplTest extends PlatformTestCase {
  private PsiDocumentManagerImpl getPsiDocumentManager() {
    return (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject());
  }

  public void testGetCachedPsiFile_NoFile() throws Exception {
    final PsiFile file = getPsiDocumentManager().getCachedPsiFile(new MockDocument());
    assertNull(file);
  }

  public void testGetPsiFile_NotRegisteredDocument() throws Exception {
    final PsiFile file = getPsiDocumentManager().getPsiFile(new MockDocument());
    assertNull(file);
  }

  public void testGetDocument_FirstGet() throws Exception {
    VirtualFile vFile = createFile();
    final PsiFile file = new MockPsiFile(vFile, getPsiManager());

    final Document document = getPsiDocumentManager().getDocument(file);
    assertNotNull(document);
    assertSame(document, FileDocumentManager.getInstance().getDocument(vFile));
  }

  @Override
  protected boolean isRunInWriteAction() {
    return false;
  }

  private static LightVirtualFile createFile() {
    return new LightVirtualFile("foo.java");
  }

  public void testDocumentGced() throws Exception {
    VirtualFile vFile = createFile();
    PsiDocumentManagerImpl documentManager = getPsiDocumentManager();
    long id = System.identityHashCode(documentManager.getDocument(getPsiManager().findFile(vFile)));

    documentManager.commitAllDocuments();
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.dispatchAllInvocationEvents();
    assertEmpty(documentManager.getUncommittedDocuments());

    LeakHunter.checkLeak(documentManager, DocumentImpl.class);
    LeakHunter.checkLeak(documentManager, PsiFileImpl.class, new Processor<PsiFileImpl>() {
      @Override
      public boolean process(PsiFileImpl psiFile) {
        return psiFile.getViewProvider().getVirtualFile().getFileSystem() instanceof LocalFileSystem;
      }
    });
    //Class.forName("com.intellij.util.ProfilingUtil").getDeclaredMethod("forceCaptureMemorySnapshot").invoke(null);

    Reference<Document> reference = vFile.getUserData(FileDocumentManagerImpl.DOCUMENT_KEY);
    assertNotNull(reference);
    for (int i=0;i<1000;i++) {
      UIUtil.dispatchAllInvocationEvents();
      if (reference.get() == null) break;
      System.gc();
    }
    assertNull(documentManager.getCachedDocument(getPsiManager().findFile(vFile)));

    Document newDoc = documentManager.getDocument(getPsiManager().findFile(vFile));
    assertTrue(id != System.identityHashCode(newDoc));
  }

  public void testGetUncommittedDocuments_noDocuments() throws Exception {
    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testGetUncommittedDocuments_documentChanged_DontProcessEvents() throws Exception {
    PsiFile file = getPsiManager().findFile(createFile());

    final Document document = getPsiDocumentManager().getDocument(file);

    final TextBlock block = TextBlock.get(file);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        block.performAtomically(new Runnable() {
          @Override
          public void run() {
            getPsiDocumentManager().documentChanged(new DocumentEventImpl(document, 0, "", "", document.getModificationStamp(), false));
          }
        });
      }
    });


    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testGetUncommittedDocuments_documentNotRegistered() throws Exception {
    final Document document = new MockDocument();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        getPsiDocumentManager().documentChanged(new DocumentEventImpl(document, 0, "", "", document.getModificationStamp(), false));
      }
    });


    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testCommitDocument_RemovesFromUncommittedList() throws Exception {
    PsiFile file = getPsiManager().findFile(createFile());

    final Document document = getPsiDocumentManager().getDocument(file);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        getPsiDocumentManager().documentChanged(new DocumentEventImpl(document, 0, "", "", document.getModificationStamp(), false));
      }
    });


    getPsiDocumentManager().commitDocument(document);
    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testCommitAllDocument_RemovesFromUncommittedList() throws Exception {
    PsiFile file = getPsiManager().findFile(createFile());

    final Document document = getPsiDocumentManager().getDocument(file);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        getPsiDocumentManager().documentChanged(new DocumentEventImpl(document, 0, "", "", document.getModificationStamp(), false));
      }
    });


    getPsiDocumentManager().commitAllDocuments();
    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testDocumentFromAlienProjectDoesNotEndUpInMyUncommittedList() throws Exception {
    PsiFile file = getPsiManager().findFile(createFile());

    final Document document = getPsiDocumentManager().getDocument(file);

    File temp = createTempDirectory();
    final Project alienProject = createProject(new File(temp, "alien.ipr"), DebugUtil.currentStackTrace());
    boolean succ2 = ProjectManagerEx.getInstanceEx().openProject(alienProject);
    assertTrue(succ2);


    try {
      PsiManager alienManager = PsiManager.getInstance(alienProject);
      final String alienText = "alien";

      LightVirtualFile alienVirt = new LightVirtualFile("foo.java", alienText);
      final PsiFile alienFile = alienManager.findFile(alienVirt);
      final PsiDocumentManagerImpl alienDocManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(alienProject);
      final Document alienDocument = alienDocManager.getDocument(alienFile);
      //alienDocument.putUserData(CACHED_VIEW_PROVIDER, new MockFileViewProvider(alienFile));
      assertEquals(0, alienDocManager.getUncommittedDocuments().length);
      assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          getPsiDocumentManager()
            .documentChanged(new DocumentEventImpl(alienDocument, 0, "", "", alienDocument.getModificationStamp(), false));
          assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
          assertEquals(0, alienDocManager.getUncommittedDocuments().length);

          alienDocManager.documentChanged(new DocumentEventImpl(alienDocument, 0, "", "", alienDocument.getModificationStamp(), false));
          assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
          assertEquals(1, alienDocManager.getUncommittedDocuments().length);

          getPsiDocumentManager().documentChanged(new DocumentEventImpl(document, 0, "", "", document.getModificationStamp(), false));
          assertEquals(1, getPsiDocumentManager().getUncommittedDocuments().length);
          assertEquals(1, alienDocManager.getUncommittedDocuments().length);

          alienDocManager.documentChanged(new DocumentEventImpl(document, 0, "", "", document.getModificationStamp(), false));
          assertEquals(1, getPsiDocumentManager().getUncommittedDocuments().length);
          assertEquals(1, alienDocManager.getUncommittedDocuments().length);
        }
      });
    }
    finally {
      ProjectUtil.closeAndDispose(alienProject);
    }
  }

  public void testCommitInBackground() {
    PsiFile file = getPsiManager().findFile(createFile());
    assertNotNull(file);
    assertTrue(file.isPhysical());
    final Document document = getPsiDocumentManager().getDocument(file);
    assertNotNull(document);

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    getPsiDocumentManager().performWhenAllCommitted(new Runnable() {
      @Override
      public void run() {
        assertTrue(getPsiDocumentManager().isCommitted(document));
        semaphore.up();
      }
    });
    waitAndPump(semaphore, 30000);
    assertTrue(getPsiDocumentManager().isCommitted(document));

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "class X {}");
      }
    });

    semaphore.down();
    getPsiDocumentManager().performWhenAllCommitted(new Runnable() {
      @Override
      public void run() {
        assertTrue(getPsiDocumentManager().isCommitted(document));
        semaphore.up();
      }
    });
    waitAndPump(semaphore, 30000);
    assertTrue(getPsiDocumentManager().isCommitted(document));

    final AtomicInteger count = new AtomicInteger();
    final Runnable action = new Runnable() {
      @Override
      public void run() {
        count.incrementAndGet();
      }
    };

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "/**/");
        boolean executed = getPsiDocumentManager().cancelAndRunWhenAllCommitted("xxx", action);
        assertFalse(executed);
        executed = getPsiDocumentManager().cancelAndRunWhenAllCommitted("xxx", action);
        assertFalse(executed);
        assertEquals(0, count.get());
      }
    });

    while (!getPsiDocumentManager().isCommitted(document)) {
      UIUtil.dispatchAllInvocationEvents();
    }
    assertTrue(getPsiDocumentManager().isCommitted(document));
    assertEquals(1, count.get());

    count.set(0);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "/**/");
        boolean executed = getPsiDocumentManager().performWhenAllCommitted(action);
        assertFalse(executed);
        executed = getPsiDocumentManager().performWhenAllCommitted(action);
        assertFalse(executed);
        assertEquals(0, count.get());
      }
    });

    while (!getPsiDocumentManager().isCommitted(document)) {
      UIUtil.dispatchAllInvocationEvents();
    }
    assertTrue(getPsiDocumentManager().isCommitted(document));
    assertEquals(2, count.get());
  }

  private static void waitAndPump(Semaphore semaphore, int timeout) {
    final long limit = System.currentTimeMillis() + timeout;
    while (System.currentTimeMillis() < limit) {
      if (semaphore.waitFor(10)) return;
      UIUtil.dispatchAllInvocationEvents();
    }
    fail("Timeout");
  }

  public void testDocumentFromAlienProjectGetsCommittedInBackground() throws Exception {
    LightVirtualFile virtualFile = createFile();
    PsiFile file = getPsiManager().findFile(virtualFile);

    final Document document = getPsiDocumentManager().getDocument(file);

    File temp = createTempDirectory();
    final Project alienProject = createProject(new File(temp, "alien.ipr"), DebugUtil.currentStackTrace());
    boolean succ2 = ProjectManagerEx.getInstanceEx().openProject(alienProject);
    assertTrue(succ2);


    try {
      PsiManager alienManager = PsiManager.getInstance(alienProject);

      final PsiFile alienFile = alienManager.findFile(virtualFile);
      assertNotNull(alienFile);
      final PsiDocumentManagerImpl alienDocManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(alienProject);
      final Document alienDocument = alienDocManager.getDocument(alienFile);
      assertSame(document, alienDocument);
      assertEquals(0, alienDocManager.getUncommittedDocuments().length);
      assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          document.setText("xxx");
          assertOrderedEquals(getPsiDocumentManager().getUncommittedDocuments(), document);
          assertOrderedEquals(alienDocManager.getUncommittedDocuments(), alienDocument);
        }
      });
      assertEquals("xxx", document.getText());
      assertEquals("xxx", alienDocument.getText());

      while (!getPsiDocumentManager().isCommitted(document)) {
        UIUtil.dispatchAllInvocationEvents();
      }
      long start = System.currentTimeMillis();
      while (!alienDocManager.isCommitted(alienDocument) && System.currentTimeMillis()-start < 20000) {
        UIUtil.dispatchAllInvocationEvents();
      }
      assertTrue("Still not committed: "+alienDocument, alienDocManager.isCommitted(alienDocument));
    }
    finally {
      ProjectUtil.closeAndDispose(alienProject);
    }
  }
}
