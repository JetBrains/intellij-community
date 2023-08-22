// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.FileContentImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;


public class CoreStubTreeLoader extends StubTreeLoader {
  @Override
  public ObjectStubTree<?> readOrBuild(@NotNull Project project, @NotNull VirtualFile vFile, @Nullable PsiFile psiFile) {
    if (!canHaveStub(vFile)) {
      return null;
    }

    return build(project, vFile, psiFile);
  }

  @Override
  public @Nullable ObjectStubTree<?> build(@Nullable Project project,
                                           @NotNull VirtualFile vFile,
                                           @Nullable PsiFile psiFile) {
    try {
      FileContent fc = FileContentImpl.createByFile(vFile, project);
      Stub element = StubTreeBuilder.buildStubTree(fc);
      if (element instanceof PsiFileStub) {
        return new StubTree((PsiFileStub)element);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    return null;
  }

  @Override
  public ObjectStubTree<?> readFromVFile(@NotNull Project project, @NotNull VirtualFile vFile) {
    return null;
  }

  @Override
  public void rebuildStubTree(VirtualFile virtualFile) {
  }

  @Override
  public boolean canHaveStub(VirtualFile file) {
    FileType fileType = file.getFileType();
    if (fileType instanceof LanguageFileType) {
      Language l = ((LanguageFileType)fileType).getLanguage();
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
      if (parserDefinition == null) return false;
      IFileElementType elementType = parserDefinition.getFileNodeType();
      return elementType instanceof IStubFileElementType && ((IStubFileElementType<?>)elementType).shouldBuildStubFor(file);
    }
    else if (fileType.isBinary()) {
      BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
      return builder != null && builder.acceptsFile(file);
    }
    return false;
  }
}
