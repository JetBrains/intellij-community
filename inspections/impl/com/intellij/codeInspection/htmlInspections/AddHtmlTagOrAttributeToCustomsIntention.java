/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;

/**
 * Created by IntelliJ IDEA.
* User: Maxim.Mossienko
* Date: Jul 6, 2006
* Time: 5:08:37 PM
* To change this template use File | Settings | File Templates.
*/
public class AddHtmlTagOrAttributeToCustomsIntention implements IntentionAction {
  private String myName;
  private int myType;
  private PsiElement myPsiElement;
  private XmlEntitiesInspection myEntitiesInspection;

  public AddHtmlTagOrAttributeToCustomsIntention(XmlEntitiesInspection entitiesInspection, PsiElement psiElement, String name, int type) {
    myEntitiesInspection = entitiesInspection;
    myPsiElement = psiElement;
    myName = name;
    myType = type;
  }

  public String getText() {
    if (myType == XmlEntitiesInspection.UNKNOWN_TAG) {
      return QuickFixBundle.message("add.custom.html.tag", myName);
    }

    if (myType == XmlEntitiesInspection.UNKNOWN_ATTRIBUTE) {
      return QuickFixBundle.message("add.custom.html.attribute", myName);
    }

    if (myType == XmlEntitiesInspection.NOT_REQUIRED_ATTRIBUTE) {
      return QuickFixBundle.message("add.optional.html.attribute", myName);
    }

    return null;
  }

  public String getFamilyName() {
    return QuickFixBundle.message("fix.html.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myEntitiesInspection.setAdditionalEntries(
      myType,
      appendName(myEntitiesInspection.getAdditionalEntries(myType))
    );

    final InspectionProfile inspectionProfile =
      InspectionProjectProfileManager.getInstance(project).getInspectionProfile(myPsiElement);
    //correct save settings
    ((InspectionProfileImpl)inspectionProfile).isProperSetting(HighlightDisplayKey.find(HtmlStyleLocalInspection.SHORT_NAME));
    inspectionProfile.save();
  }

  public boolean startInWriteAction() {
    return false;
  }

  private String appendName(String toAppend) {
    if (toAppend.length() > 0) {
      toAppend += "," + myName;
    }
    else {
      toAppend = myName;
    }
    return toAppend;
  }

}
