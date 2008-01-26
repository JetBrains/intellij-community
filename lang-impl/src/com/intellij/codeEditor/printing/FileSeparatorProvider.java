/*
 * User: anna
 * Date: 25-Jan-2008
 */
package com.intellij.codeEditor.printing;

import com.intellij.codeInsight.daemon.impl.LineMarkerInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FileSeparatorProvider {
  public static FileSeparatorProvider getInstance() {
    return ServiceManager.getService(FileSeparatorProvider.class);
  }

  @Nullable
  public List<LineMarkerInfo> getFileSeparators(PsiFile file, final Document document) {
    return null;
  }
}