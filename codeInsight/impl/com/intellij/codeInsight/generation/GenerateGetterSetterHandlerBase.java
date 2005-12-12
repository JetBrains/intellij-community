package com.intellij.codeInsight.generation;

import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.j2ee.ejb.EjbRolesUtil;
import com.intellij.j2ee.ejb.role.EjbClassRole;
import com.intellij.j2ee.j2eeDom.ejb.CmpField;
import com.intellij.j2ee.j2eeDom.ejb.EntityBean;
import com.intellij.j2ee.j2eeDom.xmlData.ObjectsList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;

abstract class GenerateGetterSetterHandlerBase extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateGetterSetterHandlerBase");

  public GenerateGetterSetterHandlerBase(String chooserTitle) {
    super(chooserTitle);
  }

  protected Object[] getAllOriginalMembers(PsiClass aClass) {
    ArrayList array = new ArrayList();

    try{
      PsiField[] fields = aClass.getFields();
      for (PsiField field : fields) {
        if (generateMemberPrototypes(aClass, field).length > 0) {
          array.add(field);
        }
      }

      getCmpFields(array, aClass);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }

    return array.toArray(new Object[array.size()]);
  }

  private void getCmpFields(ArrayList list, PsiClass psiClass) throws IncorrectOperationException {
    final EjbClassRole classRole = EjbRolesUtil.getEjbRole(psiClass);
    if (classRole == null || classRole.getType() != EjbClassRole.EJB_CLASS_ROLE_EJB_CLASS) return;
    if (!EjbUtil.isCMP2x(classRole.getEnterpriseBean())) return;
    ObjectsList<CmpField> cmpFields = ((EntityBean)classRole.getEjb()).getCmpFields();
    for (int i = 0; i < cmpFields.size(); i++) {
      CmpField field = cmpFields.get(i);
      if (generateMemberPrototypes(psiClass, field).length > 0) {
        list.add(field);
      }
    }
  }

}