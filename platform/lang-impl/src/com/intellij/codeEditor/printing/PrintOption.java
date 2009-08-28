/*
 * User: anna
 * Date: 25-Jan-2008
 */
package com.intellij.codeEditor.printing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

public interface PrintOption extends UnnamedConfigurable {
  ExtensionPointName<PrintOption> EP_NAME = ExtensionPointName.create("com.intellij.printOption");
  
  @Nullable
  TreeMap<Integer, PsiReference> collectReferences(PsiFile psiFile, Map<PsiFile, PsiFile> filesMap);
}