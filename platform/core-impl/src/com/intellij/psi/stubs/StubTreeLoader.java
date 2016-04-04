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
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public abstract class StubTreeLoader {

  public static StubTreeLoader getInstance() {
    return ServiceManager.getService(StubTreeLoader.class);
  }

  @Nullable
  public abstract ObjectStubTree readOrBuild(Project project, final VirtualFile vFile, @Nullable final PsiFile psiFile);

  @Nullable
  public abstract ObjectStubTree readFromVFile(Project project, final VirtualFile vFile);
  
  public abstract void rebuildStubTree(VirtualFile virtualFile);

  public abstract boolean canHaveStub(VirtualFile file);

  public String getStubAstMismatchDiagnostics(@NotNull VirtualFile file,
                                              @NotNull PsiFile psiFile,
                                              @NotNull ObjectStubTree stubTree,
                                              @Nullable Document prevCachedDocument) {
    String msg = "";
    msg += "\n file=" + psiFile;
    msg += ", file.class=" + psiFile.getClass();
    msg += ", file.lang=" + psiFile.getLanguage();
    msg += ", modStamp=" + psiFile.getModificationStamp();

    if (!(psiFile instanceof PsiCompiledElement)) {
      String text = psiFile.getText();
      PsiFile fromText = PsiFileFactory.getInstance(psiFile.getProject()).createFileFromText(psiFile.getName(), psiFile.getFileType(), text);
      if (fromText.getLanguage().equals(psiFile.getLanguage())) {
        boolean consistent = DebugUtil.psiToString(psiFile, true).equals(DebugUtil.psiToString(fromText, true));
        if (consistent) {
          msg += "\n tree consistent";
        } else {
          msg += "\n AST INCONSISTENT, perhaps after incremental reparse; " + fromText;
        }
      }
    }

    msg += "\n stub debugInfo=" + stubTree.getDebugInfo();
    msg += "\n document before=" + prevCachedDocument;

    ObjectStubTree latestIndexedStub = readFromVFile(psiFile.getProject(), file);
    msg += "\nlatestIndexedStub=" + latestIndexedStub;
    if (latestIndexedStub != null) {
      msg += "\n   same size=" + (stubTree.getPlainList().size() == latestIndexedStub.getPlainList().size());
      msg += "\n   debugInfo=" + latestIndexedStub.getDebugInfo();
    }

    FileViewProvider viewProvider = psiFile.getViewProvider();
    msg += "\n viewProvider=" + viewProvider;
    msg += "\n viewProvider stamp: " + viewProvider.getModificationStamp();

    msg += "; file stamp: " + file.getModificationStamp();
    msg += "; file modCount: " + file.getModificationCount();
    msg += "; file length: " + file.getLength();

    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document != null) {
      msg += "\n doc saved: " + !FileDocumentManager.getInstance().isDocumentUnsaved(document);
      msg += "; doc stamp: " + document.getModificationStamp();
      msg += "; doc size: " + document.getTextLength();
      msg += "; committed: " + PsiDocumentManager.getInstance(psiFile.getProject()).isCommitted(document);
    }
    return msg;
  }
  
  public static String getFileViewProviderMismatchDiagnostics(@NotNull FileViewProvider provider) {
    final Function<Language, String> languageID = new Function<Language, String>() {
      @Override
      public String fun(Language language) {
        return language.getID();
      }
    };
    final Function<PsiFile, String> fileClassName = new Function<PsiFile, String>() {
      @Override
      public String fun(PsiFile file) {
        return file.getClass().getSimpleName();
      }
    };
    final Function<PsiFile, String> fileToFileType = new Function<PsiFile, String>() {
      @Override
      public String fun(PsiFile file) {
        return file.getFileType().getName();
      }
    };
    final Function<Pair<IStubFileElementType, PsiFile>, String> stubRootToString = new Function<Pair<IStubFileElementType, PsiFile>, String>() {
      @Override
      public String fun(Pair<IStubFileElementType, PsiFile> pair) {
        return "(" + pair.first.toString() + ", " + pair.first.getLanguage() + " -> " + fileClassName.fun(pair.second) + ")";
      }
    };
    final List<Pair<IStubFileElementType, PsiFile>> roots = StubTreeBuilder.getStubbedRoots(provider);
    return "path = " + provider.getVirtualFile().getPath() +
           ", stubBindingRoot = " + fileClassName.fun(provider.getStubBindingRoot()) +
           ", languages = [" + StringUtil.join(provider.getLanguages(), languageID, ", ") +
           "], filesTypes = [" + StringUtil.join(provider.getAllFiles(), fileToFileType, ", ") +
           "], files = [" + StringUtil.join(provider.getAllFiles(), fileClassName, ", ") +
           "], roots = [" + StringUtil.join(roots, stubRootToString, ", ") + "]";
  }
}
