package com.intellij.codeInsight.generation;

import com.intellij.javaee.ejb.role.EjbClassRole;
import com.intellij.javaee.ejb.role.EjbClassRoleEnum;
import com.intellij.javaee.ejb.role.EjbRolesUtil;
import com.intellij.javaee.model.common.CmpField;
import com.intellij.javaee.model.common.EntityBean;
import com.intellij.javaee.model.common.EnterpriseBean;
import com.intellij.javaee.model.enums.CmpVersion;
import com.intellij.javaee.model.enums.PersistenceType;
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
    ArrayList<Object> array = new ArrayList<Object>();

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

  private void getCmpFields(ArrayList<Object> list, PsiClass psiClass) throws IncorrectOperationException {
    final EjbClassRole classRole = EjbRolesUtil.getEjbRolesUtil().getEjbRole(psiClass);
    if (classRole == null || classRole.getType() != EjbClassRoleEnum.EJB_CLASS_ROLE_EJB_CLASS) return;

    final EnterpriseBean ejb = classRole.getEnterpriseBean();
    if (!(ejb instanceof EntityBean)) return;

    final EntityBean entityBean = (EntityBean)ejb;
    if (entityBean.getCmpVersion().getValue() != CmpVersion.CmpVersion_2_X ||
             entityBean.getPersistenceType().getValue() != PersistenceType.CONTAINER) return;


    for (final CmpField field : ((EntityBean)classRole.getEnterpriseBean()).getCmpFields()) {
      if (generateMemberPrototypes(psiClass, field).length > 0) {
        list.add(field);
      }
    }
  }

}