/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class GenerateGetterSetterHandlerBase extends GenerateMembersHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateGetterSetterHandlerBase");

  static {
    GenerateAccessorProviderRegistrar.registerProvider(new NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>() {
      @Override
      @NotNull
      public Collection<EncapsulatableClassMember> fun(PsiClass s) {
        if (s.getLanguage() != StdLanguages.JAVA) return Collections.emptyList();
        final List<EncapsulatableClassMember> result = new ArrayList<EncapsulatableClassMember>();
        for (PsiField field : s.getFields()) {
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

  @Override
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project, Editor editor) {
    final ClassMember[] allMembers = getAllOriginalMembers(aClass);
    if (allMembers == null) {
      HintManager.getInstance().showErrorHint(editor, getNothingFoundMessage());
      return null;
    }
    if (allMembers.length == 0) {
      HintManager.getInstance().showErrorHint(editor, getNothingAcceptedMessage());
      return null;
    }
    return chooseMembers(allMembers, false, false, project, editor);
  }

  @Override
  protected abstract String getNothingFoundMessage();
  protected abstract String getNothingAcceptedMessage();

  public boolean canBeAppliedTo(PsiClass targetClass) {
    final ClassMember[] allMembers = getAllOriginalMembers(targetClass);
    return allMembers != null && allMembers.length != 0;
  }

  @Override
  @Nullable
  protected ClassMember[] getAllOriginalMembers(final PsiClass aClass) {
    final List<EncapsulatableClassMember> list = GenerateAccessorProviderRegistrar.getEncapsulatableClassMembers(aClass);
    if (list.isEmpty()) {
      return null;
    }
    final List<EncapsulatableClassMember> members = ContainerUtil.findAll(list, new Condition<EncapsulatableClassMember>() {
      @Override
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
