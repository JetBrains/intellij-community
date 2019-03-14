// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

public abstract class FileContentHashContributor implements HashContributor<FileContent> {
  public static HashContributor<FileContent> create(IndexExtension<?, ?, FileContent> extension) {
    return extension instanceof PsiDependentIndex ? new PsiContentHashContributor() : new ContentHashContributorImpl();
  }

  @Override
  public void updateHash(@NotNull FileContent content, @NotNull Hasher hasher) {
    hasher.append(content.getFileType().getName());
    hasher.append(content instanceof FileContentImpl ? ((FileContentImpl)content).getCharset().name() : "null_charset");
    byte[] bytes = getBytes(content);
    hasher.append(bytes.length);
    hasher.append(bytes);
  }

  protected abstract byte[] getBytes(@NotNull FileContent content);
  // psi backed index should use existing psi to build index value (FileContentImpl.getPsiFileForPsiDependentIndex())
  // so we should use different bytes to calculate hash(Id)
  private static class PsiContentHashContributor extends FileContentHashContributor {
    @Override
    protected byte[] getBytes(@NotNull FileContent content) {
      Document document = FileDocumentManager.getInstance().getCachedDocument(content.getFile());

      if (document != null) {  // if document is not committed
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(content.getProject());

        if (psiDocumentManager.isUncommited(document)) {
          PsiFile file = psiDocumentManager.getCachedPsiFile(document);
          Charset charset = ((FileContentImpl)content).getCharset();

          if (file != null) {
            return file.getText().getBytes(charset);
          }
        }
      }
      return content.getContent();
    }

    @NotNull
    @Override
    public String getId() {
      return "uncommitted.hash.id";
    }
  }

  private static class ContentHashContributorImpl extends FileContentHashContributor {
    @Override
    protected byte[] getBytes(@NotNull FileContent content) {
      return content.getContent();
    }

    @NotNull
    @Override
    public String getId() {
      return "content.hash.id";
    }
  }
}
