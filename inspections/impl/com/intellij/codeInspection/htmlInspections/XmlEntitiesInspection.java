/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;

/**
 * User: anna
 * Date: 16-Dec-2005
 */
public interface XmlEntitiesInspection {
  int UNKNOWN_TAG = 1;
  int UNKNOWN_ATTRIBUTE = 2;
  int NOT_REQUIRED_ATTRIBUTE = 3;
  
  IntentionAction getIntentionAction(PsiElement psiElement, String name, int type);
  String getAdditionalEntries(int type);
  void setAdditionalEntries(int type, String additionalEntries);
}
