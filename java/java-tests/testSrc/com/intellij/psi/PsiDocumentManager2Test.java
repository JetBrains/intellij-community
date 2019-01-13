// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.highlighter.XmlLikeFileType;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@PlatformTestCase.WrapInCommand
public class PsiDocumentManager2Test extends LightPlatformTestCase {
  public void testUnregisteredFileType() {
    class MyFileType extends XmlLikeFileType {
      private MyFileType() {
        super(StdLanguages.XML);
      }

      @Override
      @NotNull
      public String getName() {
        return "MY";
      }

      @Override
      @NotNull
      public String getDefaultExtension() {
        return "my";
      }

      @Override
      @NotNull
      public String getDescription() {
        return "my own";
      }

      @Override
      @Nullable
      public Icon getIcon() {
        return null;
      }
    }
    PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText("DummyFile.my", new MyFileType(), "<gggg></gggg>", LocalTimeCounter.currentTime(), true);
    Document document = ObjectUtils.assertNotNull(PsiDocumentManager.getInstance(getProject()).getDocument(file));

    assertTrue(document.isWritable());
    assertTrue(file.getVirtualFile().getFileType() instanceof MyFileType);
    assertTrue(DaemonCodeAnalyzer.getInstance(getProject()).isHighlightingAvailable(file));
  }

  public void testPsiToDocSyncInNonPhysicalFile() {
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject())
      .createFileFromText("a.java", JavaLanguage.INSTANCE, "class Foo {}", false, false);

    Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());
    assertNotNull(document);

    file.getClasses()[0].getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);

    assertEquals("public class Foo {}", document.getText());
    assertFalse(PsiDocumentManager.getInstance(getProject()).isUncommited(document));
  }

  public void testDocumentCanSuddenlyBecomeUncommittedIfLightAndTextEditorBackgroundHighlighterDoesntFreakOutAtThatMoment() throws Exception {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assertEmpty(PsiDocumentManager.getInstance(getProject()).getUncommittedDocuments());
    PsiJavaCodeReferenceCodeFragment fragment =
      JavaCodeFragmentFactory.getInstance(getProject()).createReferenceCodeFragment("xxx", null, false, true);

    VirtualFile file = fragment.getViewProvider().getVirtualFile();
    Document document = ObjectUtils.assertNotNull(FileDocumentManager.getInstance().getDocument(file));

    assertNotNull(file);
    FileViewProvider provider = fragment.getViewProvider();
    assertNotNull(provider.getPsi(fragment.getLanguage()));
    assertNotNull(provider);
    assertSame(provider, ((PsiManagerEx)getPsiManager()).getFileManager().findCachedViewProvider(file));

    Future<Document> modification = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() -> {
      document.insertString(0, "c");

      Document uncommitted = assertOneElement(PsiDocumentManager.getInstance(getProject()).getUncommittedDocuments());
      assertSame(document, uncommitted);

      return document;
    }));
    PsiFile toHighlight = createFile("x.txt", "text");
    FileEditor fileEditor = TextEditorProvider.getInstance().createEditor(getProject(), toHighlight.getVirtualFile());
    try {
      CompletableFuture<?> passesCreated = new CompletableFuture<>();
      AsyncEditorLoader.performWhenLoaded(((TextEditor)fileEditor).getEditor(), () -> {
        try {
          BackgroundEditorHighlighter highlighter = fileEditor.getBackgroundHighlighter();
          highlighter.createPassesForEditor(); // check didn't crash in TextEditorBackgroundHighlighter.getPasses() isCommitted() check
          passesCreated.complete(null);
        }
        catch (Throwable e) {
          passesCreated.completeExceptionally(e);
        }
      });

      long start = System.currentTimeMillis();
      while (!PsiDocumentManager.getInstance(getProject()).isCommitted(document) || !modification.isDone() || !passesCreated.isDone()) {
        UIUtil.dispatchAllInvocationEvents();
        assertTrue(System.currentTimeMillis() < start + 100 * 1000);
      }
      modification.get();
      passesCreated.get();
    }
    finally {
      Disposer.dispose(fileEditor);
    }
  }
}
