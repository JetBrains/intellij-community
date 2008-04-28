package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class CreateGetterOrSetterFix implements IntentionAction{
  private final boolean myCreateGetter;
  private final boolean myCreateSetter;
  private final PsiField myField;
  private final String myPropertyName;

  public CreateGetterOrSetterFix(boolean createGetter, boolean createSetter, PsiField field) {
    myCreateGetter = createGetter;
    myCreateSetter = createSetter;
    myField = field;
    Project project = field.getProject();
    myPropertyName = PropertyUtil.suggestPropertyName(project, field);
  }

  @NotNull
  public String getText() {
    @NonNls final String what;
    if (myCreateGetter && myCreateSetter) {
      what = "create.getter.and.setter.for.field";
    }
    else if (myCreateGetter) {
      what = "create.getter.for.field";
    }
    else if (myCreateSetter) {
      what = "create.setter.for.field";
    }
    else {
      what = "";
      assert false;
    }
    return QuickFixBundle.message(what, myField.getName());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.accessor.for.unused.field.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myField.isValid()) return false;
    PsiClass aClass = myField.getContainingClass();
    if (aClass == null) {
      return false;
    }
    if (myCreateGetter && PropertyUtil.findPropertyGetter(aClass, myPropertyName, myField.hasModifierProperty(PsiModifier.STATIC), false) != null) {
      return false;
    }
    if (myCreateSetter && PropertyUtil.findPropertySetter(aClass, myPropertyName, myField.hasModifierProperty(PsiModifier.STATIC), false) != null) {
      return false;
    }
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiClass aClass = myField.getContainingClass();
    if (myCreateGetter) {
      aClass.add(PropertyUtil.generateGetterPrototype(myField));
    }
    if (myCreateSetter) {
      aClass.add(PropertyUtil.generateSetterPrototype(myField));
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
