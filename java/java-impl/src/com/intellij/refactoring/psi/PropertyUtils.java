package com.intellij.refactoring.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PropertyUtil;

public class PropertyUtils {
    private PropertyUtils() {
    }

    public static PsiMethod findSetterForField(PsiField field) {
        final PsiClass containingClass = field.getContainingClass();
        final Project project = field.getProject();
        final String propertyName = PropertyUtil.suggestPropertyName(project, field);
        final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        return PropertyUtil.findPropertySetter(containingClass, propertyName, isStatic, true);
    }

    public static PsiMethod findGetterForField(PsiField field) {
        final PsiClass containingClass = field.getContainingClass();
        final Project project = field.getProject();
        final String propertyName = PropertyUtil.suggestPropertyName(project, field);
        final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        return PropertyUtil.findPropertyGetter(containingClass, propertyName, isStatic, true);
    }
}
