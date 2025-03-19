// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

public class RenameViewDescriptor implements UsageViewDescriptor{
  private final @NlsContexts.ListItem String myProcessedElementsHeader;
  private final @Nls String myCodeReferencesText;
  private final PsiElement[] myElements;

  public RenameViewDescriptor(LinkedHashMap<PsiElement, String> renamesMap) {

    myElements = PsiUtilCore.toPsiElementArray(renamesMap.keySet());

    Set<String> processedElementsHeaders = new HashSet<>();
    Set<String> codeReferences = new HashSet<>();

    for (final PsiElement element : myElements) {
      PsiUtilCore.ensureValid(element);
      String newName = renamesMap.get(element);

      String prefix = "";
      if (element instanceof PsiDirectory) {
        String fullName = UsageViewUtil.getLongName(element);
        int lastSlash = fullName.lastIndexOf('/');
        if (lastSlash >= 0) {
          prefix = fullName.substring(0, lastSlash + 1);
        }
      }

      processedElementsHeaders.add(RefactoringBundle.message("0.to.be.renamed.to.1.2", UsageViewUtil.getType(element), prefix, newName));
      codeReferences.add(UsageViewUtil.getType(element) + " " + UsageViewUtil.getLongName(element));
    }


    myProcessedElementsHeader = StringUtil.capitalize(StringUtil.join(ArrayUtilRt.toStringArray(processedElementsHeaders), ", "));
    myCodeReferencesText =
      RefactoringBundle.message("references.in.code.to.0", StringUtil.join(ArrayUtilRt.toStringArray(codeReferences), ", "));
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return myElements;
  }

  @Override
  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return myCodeReferencesText + UsageViewBundle.getReferencesString(usagesCount, filesCount);
  }

  @Override
  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("comments.elements.header",
                                     UsageViewBundle.getOccurencesString(usagesCount, filesCount));
  }

}