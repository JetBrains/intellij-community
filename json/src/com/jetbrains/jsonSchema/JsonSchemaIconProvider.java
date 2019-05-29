// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Irina.Chernushina on 5/23/2017.
 */
public class JsonSchemaIconProvider extends IconProvider {
  @Nullable
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element instanceof PsiFile) {
      VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if (virtualFile != null
          && JsonSchemaEnabler.EXTENSION_POINT_NAME.getExtensionList().stream().anyMatch(e -> e.canBeSchemaFile(virtualFile))
          && JsonSchemaService.isSchemaFile((PsiFile)element)) {
        return AllIcons.FileTypes.JsonSchema;
      }
    }
    return null;
  }
}
