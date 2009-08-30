package com.intellij.psi.presentation.java;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JavaPresentationUtil {
  private JavaPresentationUtil() {
  }

  public static ItemPresentation getMethodPresentation(final PsiMethod psiMethod) {
    return new ItemPresentation() {
      public String getPresentableText() {
        return PsiFormatUtil.formatMethod(
          psiMethod,
          PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
          PsiFormatUtil.SHOW_TYPE
        );
      }

      public TextAttributesKey getTextAttributesKey() {
        if (psiMethod.isDeprecated()) {
          return CodeInsightColors.DEPRECATED_ATTRIBUTES;
        }
        return null;
      }

      public String getLocationString() {
        return getJavaSymbolContainerText(psiMethod);
      }

      public Icon getIcon(boolean open) {
        return psiMethod.getIcon(Iconable.ICON_FLAG_VISIBILITY);
      }
    };
  }

  public static ItemPresentation getFieldPresentation(final PsiField psiField) {
    return new ItemPresentation() {
      public String getPresentableText() {
        return psiField.getName();
      }

      public TextAttributesKey getTextAttributesKey() {
        if (psiField.isDeprecated()) {
          return CodeInsightColors.DEPRECATED_ATTRIBUTES;
        }
        return null;
      }

      public String getLocationString() {
        return getJavaSymbolContainerText(psiField);
      }

      public Icon getIcon(boolean open) {
        return psiField.getIcon(Iconable.ICON_FLAG_VISIBILITY);
      }
    };
  }

  public static ItemPresentation getVariablePresentation(final PsiVariable variable) {
    return new ItemPresentation() {
      public String getPresentableText() {
        return PsiFormatUtil.formatVariable(variable, PsiFormatUtil.SHOW_TYPE, PsiSubstitutor.EMPTY);
      }

      public String getLocationString() {
        return "";
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return variable.getIcon(Iconable.ICON_FLAG_OPEN);
      }
    };
  }

  @Nullable
  private static String getJavaSymbolContainerText(final PsiElement element) {
    final String result;
    PsiElement container = PsiTreeUtil.getParentOfType(element, PsiMember.class, PsiFile.class);

    if (container instanceof PsiClass) {
      String qName = ((PsiClass)container).getQualifiedName();
      if (qName != null) {
        result = qName;
      }
      else {
        result = ((PsiClass)container).getName();
      }
    }
    else if (container instanceof PsiJavaFile) {
      result = ((PsiJavaFile)container).getPackageName();
    }
    else {//TODO: local classes
      result = null;
    }
    if (result != null) {
      return PsiBundle.message("aux.context.display", result);
    }
    return null;
  }
}
