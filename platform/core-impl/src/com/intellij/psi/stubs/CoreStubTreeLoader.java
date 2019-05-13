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
import com.intellij.util.indexing.IndexingDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author yole
 */
public class CoreStubTreeLoader extends StubTreeLoader {
  @Override
  public ObjectStubTree readOrBuild(Project project, VirtualFile vFile, @Nullable PsiFile psiFile) {
    if (!canHaveStub(vFile)) {
      return null;
    }

    try {
      final FileContent fc = new FileContentImpl(vFile, vFile.contentsToByteArray());
      fc.putUserData(IndexingDataKeys.PROJECT, project);
      final Stub element = StubTreeBuilder.buildStubTree(fc);
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
  public ObjectStubTree readFromVFile(Project project, VirtualFile vFile) {
    return null;
  }

  @Override
  public void rebuildStubTree(VirtualFile virtualFile) {
  }

  @Override
  public boolean canHaveStub(VirtualFile file) {
    final FileType fileType = file.getFileType();
    if (fileType instanceof LanguageFileType) {
      Language l = ((LanguageFileType)fileType).getLanguage();
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
      if (parserDefinition == null) return false;
      final IFileElementType elementType = parserDefinition.getFileNodeType();
      return elementType instanceof IStubFileElementType && ((IStubFileElementType)elementType).shouldBuildStubFor(file);
    }
    else if (fileType.isBinary()) {
      final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
      return builder != null && builder.acceptsFile(file);
    }
    return false;
  }

  @Override
  protected boolean isPrebuilt(@NotNull VirtualFile virtualFile) {
    return false;
  }
}
