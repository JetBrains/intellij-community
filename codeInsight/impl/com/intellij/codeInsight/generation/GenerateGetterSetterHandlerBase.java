package com.intellij.codeInsight.generation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class GenerateGetterSetterHandlerBase extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateGetterSetterHandlerBase");

  static {
    GenerateAccessorProviderRegistrar.registerProvider(new NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>() {
      @NotNull
      public Collection<EncapsulatableClassMember> fun(PsiClass s) {
        final List<EncapsulatableClassMember> result = new ArrayList<EncapsulatableClassMember>();
        for(PsiField field: s.getFields()) {
          if (!(field instanceof PsiEnumConstant)) {
            result.add(new PsiFieldMember(field));
          }
        }
        return result;
      }
    });
  }

  public GenerateGetterSetterHandlerBase(String chooserTitle) {
    super(chooserTitle);
  }

  protected ClassMember[] getAllOriginalMembers(final PsiClass aClass) {
    final List<EncapsulatableClassMember> list = GenerateAccessorProviderRegistrar.getEncapsulatableClassMembers(aClass);
    final List<EncapsulatableClassMember> members = ContainerUtil.findAll(list, new Condition<EncapsulatableClassMember>() {
      public boolean value(EncapsulatableClassMember member) {
        try {
          return generateMemberPrototypes(aClass, member).length > 0;
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
          return false;
        }
      }
    });
    return members.toArray(new ClassMember[members.size()]);
  }


}