/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 28, 2001
 * Time: 4:17:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiModifierListOwner;

public class RefImplicitConstructorImpl extends RefMethodImpl implements RefImplicitConstructor {

  RefImplicitConstructorImpl(RefClass ownerClass) {
    super(InspectionsBundle.message("inspection.reference.implicit.constructor.name", ownerClass.getName()), ownerClass);
  }

  public void buildReferences() {
    ((RefManagerImpl)getRefManager()).fireBuildReferences(this);
  }

  public boolean isSuspicious() {
    return ((RefClassImpl)getOwnerClass()).isSuspicious();
  }

  public String getName() {
    return InspectionsBundle.message("inspection.reference.implicit.constructor.name", getOwnerClass().getName());
  }

  public String getExternalName() {
    return getOwnerClass().getExternalName();
  }

  public boolean isValid() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return getOwnerClass().isValid();
      }
    }).booleanValue();
  }

  public String getAccessModifier() {
    return getOwnerClass().getAccessModifier();
  }

  public void setAccessModifier(String am) {
    RefJavaUtil.getInstance().setAccessModifier(getOwnerClass(), am);
  }

  public PsiModifierListOwner getElement() {
    return getOwnerClass().getElement();
  }

  public RefClass getOwnerClass() {
    return myOwnerClass == null ? super.getOwnerClass() : myOwnerClass;
  }
}
