package com.intellij.ide.impl.dataRules;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.PsiElement;

public class NavigatableRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    final OpenFileDescriptor openFileDescriptor = (OpenFileDescriptor)dataProvider.getData(DataConstants.OPEN_FILE_DESCRIPTOR);
    if (openFileDescriptor != null && openFileDescriptor.getFile().isValid()) {
      return openFileDescriptor;
    }

    final PsiElement element = (PsiElement)dataProvider.getData(DataConstants.PSI_ELEMENT);
    if (element != null) {
      return EditSourceUtil.getDescriptor(element);
    }

    return null;
  }
}
