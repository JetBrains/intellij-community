// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.AccessModifier;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.SimpleColoredComponent;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class RecordConstructorMember implements ClassMember {
  private final PsiClass myRecord;
  private final boolean myCompact;

  public RecordConstructorMember(PsiClass aRecord, boolean compact) {
    myRecord = aRecord;
    myCompact = compact;
  }
  
  @Override
  public MemberChooserObject getParentNodeDelegate() {
    final String text = PsiFormatUtil.formatClass(myRecord, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
    return new PsiDocCommentOwnerMemberChooserObject(myRecord, text, myRecord.getIcon(0));
  }

  @Override
  public void renderTreeNode(SimpleColoredComponent component, JTree tree) {
    
  }

  public boolean isCompact() {
    return myCompact;
  }

  @NotNull
  @Override
  public String getText() {
    return myCompact ? "Compact constructor" : "Canonical constructor";
  }

  @NotNull
  public PsiMethod generateRecordConstructor() {
    String constructor;
    AccessModifier accessModifier = AccessModifier.PUBLIC;
    if (PsiUtil.getLanguageLevel(myRecord) != LanguageLevel.JDK_14_PREVIEW) {
      PsiModifierList list = myRecord.getModifierList();
      if (list != null) {
        accessModifier = AccessModifier.fromModifierList(list);
      }
    }
    if (myCompact) {
      constructor = myRecord.getName() + "{\n}";
    }
    else {
      PsiRecordComponent[] components = myRecord.getRecordComponents();
      String parameters = StreamEx.of(components).map(PsiRecordComponent::getText).joining(",", "(", ")");
      String body =
        StreamEx.of(components).map(PsiRecordComponent::getName).map(name -> "this." + name + "=" + name + ";\n").joining("", "{", "}");
      constructor = myRecord.getName() + parameters + body;
    }
    Project project = myRecord.getProject();
    PsiMethod ctor = JavaPsiFacade.getElementFactory(project).createMethodFromText(constructor, myRecord);
    ctor.getModifierList().setModifierProperty(accessModifier.toPsiModifier(), true);
    if (!myCompact) {
      JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myRecord.getContainingFile());
      boolean finalParameters = settings.isGenerateFinalParameters();
      PsiParameterList parameterList = ctor.getParameterList();
      for (PsiParameter parameter : parameterList.getParameters()) {
        PsiModifierList modifierList = parameter.getModifierList();
        if (modifierList != null) {
          modifierList.setModifierProperty(PsiModifier.FINAL, finalParameters);
          PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(modifierList);
          for (PsiAnnotation annotation : parameter.getAnnotations()) {
            PsiAnnotation.TargetType applicable = AnnotationTargetUtil.findAnnotationTarget(annotation, targets);
            if (applicable == null) {
              annotation.delete();
            }
          }
        }
      }
    }
    CodeStyleManager.getInstance(project).reformat(ctor);
    return ctor;
  }
}
