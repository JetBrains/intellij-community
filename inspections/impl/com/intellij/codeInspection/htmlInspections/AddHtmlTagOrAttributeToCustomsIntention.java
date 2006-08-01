/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

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
  private String myInspectionName;

  public AddHtmlTagOrAttributeToCustomsIntention(String shortName, PsiElement psiElement, String name, int type) {
    myInspectionName = shortName;
    myPsiElement = psiElement;
    myName = name;
    myType = type;
  }

  @NotNull
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

    return getFamilyName();
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.html.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
    final InspectionProfile inspectionProfile = profileManager.getInspectionProfile(myPsiElement);
    final ModifiableModel model = inspectionProfile.getModifiableModel();
    final LocalInspectionToolWrapper wrapper = (LocalInspectionToolWrapper)model.getInspectionTool(myInspectionName);
    final XmlEntitiesInspection xmlEntitiesInspection = (XmlEntitiesInspection)wrapper.getTool();
    xmlEntitiesInspection.setAdditionalEntries(myType, appendName(xmlEntitiesInspection.getAdditionalEntries(myType)));
    model.isProperSetting(HighlightDisplayKey.find(myInspectionName));//update map with non-default settings
    model.commit(profileManager);
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
