package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Set;

class RenameViewDescriptor implements UsageViewDescriptor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameViewDescriptor");
  private final String myProcessedElementsHeader;
  private final String myCodeReferencesText;
  private final PsiElement[] myElements;

  public RenameViewDescriptor(LinkedHashMap<PsiElement, String> renamesMap) {

    myElements = renamesMap.keySet().toArray(new PsiElement[0]);

    Set<String> processedElementsHeaders = new THashSet<String>();
    Set<String> codeReferences = new THashSet<String>();

    for (final PsiElement element : myElements) {
      LOG.assertTrue(element.isValid(), "Invalid element: " + element.toString());
      String newName = renamesMap.get(element);

      String prefix = "";
      if (element instanceof PsiDirectory/* || element instanceof PsiClass*/) {
        String fullName = UsageViewUtil.getLongName(element);
        int lastDot = fullName.lastIndexOf('.');
        if (lastDot >= 0) {
          prefix = fullName.substring(0, lastDot + 1);
        }
      }

      processedElementsHeaders.add(StringUtil.capitalize(
        RefactoringBundle.message("0.to.be.renamed.to.1.2", UsageViewUtil.getType(element), prefix, newName)));
      codeReferences.add(UsageViewUtil.getType(element) + " " + UsageViewUtil.getLongName(element));
    }


    myProcessedElementsHeader = StringUtil.join(processedElementsHeaders.toArray(ArrayUtil.EMPTY_STRING_ARRAY),", ");
    myCodeReferencesText =  RefactoringBundle.message("references.in.code.to.0", StringUtil.join(codeReferences.toArray(ArrayUtil.EMPTY_STRING_ARRAY), ", "));
  }

  @NotNull
  public PsiElement[] getElements() {
    return myElements;
  }

  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return myCodeReferencesText + UsageViewBundle.getReferencesString(usagesCount, filesCount);
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("comments.elements.header",
                                     UsageViewBundle.getOccurencesString(usagesCount, filesCount));
  }

}