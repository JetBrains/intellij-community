/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.usages;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Icons;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 17, 2005
 */
public class ReadWriteAccessUsageInfo2UsageAdapter extends UsageInfo2UsageAdapter implements ReadWriteAccessUsage{
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.ReadWriteAccessUsageInfo2UsageAdapter");
  private final boolean myAccessedForReading;
  private final boolean myAccessedForWriting;

  public ReadWriteAccessUsageInfo2UsageAdapter(final UsageInfo usageInfo) {
    super(usageInfo);
    final PsiElement element = getUsageInfo().getElement();
    LOG.assertTrue(element instanceof PsiReferenceExpression);
    final PsiReferenceExpression referent = (PsiReferenceExpression)element;
    myAccessedForReading = PsiUtil.isAccessedForReading(referent);
    myAccessedForWriting = isAccessedForWriting(referent);
    if (myIcon == null) {
      if (myAccessedForReading && myAccessedForWriting) {
        myIcon = Icons.VARIABLE_RW_ACCESS;
      }
      else if (myAccessedForWriting) {
        myIcon = Icons.VARIABLE_WRITE_ACCESS;           // If icon is changed, don't forget to change UTCompositeUsageNode.getIcon();
      }
      else if (myAccessedForReading){
        myIcon = Icons.VARIABLE_READ_ACCESS;            // If icon is changed, don't forget to change UTCompositeUsageNode.getIcon();
      }
    }
  }

  public boolean isAccessedForWriting() {
    return myAccessedForWriting;
  }

  public boolean isAccessedForReading() {
    return myAccessedForReading;
  }

  private static boolean isAccessedForWriting(PsiReferenceExpression referent) {
    if (PsiUtil.isAccessedForWriting(referent)) {
      return true;
    }
    /*
    todo: when searching usages of fields, should show all found setters as a "write usage"
    if (myProcessedElementsPointers.length > 0) {
      PsiElement referee = myProcessedElementsPointers[0].getElement();
      if (referee instanceof PsiField && !referent.isReferenceTo(referee)) {
        PsiElement actualReferee = referent.resolve();
        return actualReferee instanceof PsiMethod && PropertyUtil.isSimplePropertySetter((PsiMethod)actualReferee);
      }
    }
    */
    return false;
  }


}
