/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.impl.AddAllMembersProcessor;
import com.intellij.ide.util.FileStructureNodeProvider;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class JavaInheritedMembersNodeProvider implements FileStructureNodeProvider {
  @NonNls public static final String ID = "SHOW_INHERITED";

    public String getCheckBoxText() {
      return IdeBundle.message("file.structure.toggle.show.inherited");
    }

    public Shortcut[] getShortcut() {
      return KeymapManager.getInstance().getActiveKeymap().getShortcuts("FileStructurePopup");
    }

  @Override
  public Collection<TreeElement> provideNodes(TreeElement node) {
    if (node instanceof JavaClassTreeElement) {
      final PsiClass aClass = ((JavaClassTreeElement)node).getValue();
      Collection<PsiElement> inherited = new LinkedHashSet<PsiElement>();
      Collection<PsiElement> ownChildren = JavaClassTreeElement.getOwnChildren(aClass);

      aClass.processDeclarations(new AddAllMembersProcessor(inherited, aClass), ResolveState.initial(), null, aClass);
      inherited.removeAll(ownChildren);
      if (aClass instanceof PsiAnonymousClass) {
        final PsiElement element = ((PsiAnonymousClass)aClass).getBaseClassReference().resolve();
        if (element instanceof PsiClass) {
          ContainerUtil.addAll(inherited, ((PsiClass)element).getInnerClasses());
        }
      }
      List<TreeElement> array = new ArrayList<TreeElement>();
      for (PsiElement child : inherited) {
        if (!child.isValid()) continue;
        final Set<PsiClass> parents = ((JavaClassTreeElement)node).getParents();
        if (child instanceof PsiClass && !parents.contains(child)) {
          array.add(new JavaClassTreeElement((PsiClass)child, true, parents));
        }
        else if (child instanceof PsiField) {
          array.add(new PsiFieldTreeElement((PsiField)child, true));
        }
        else if (child instanceof PsiMethod) {
          array.add(new PsiMethodTreeElement((PsiMethod)child, true));
        }
        else if (child instanceof PsiClassInitializer) {
          array.add(new ClassInitializerTreeElement((PsiClassInitializer)child));
        }
      }
      return array;
    }
    return Collections.emptyList();
  }

  @NotNull
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(IdeBundle.message("action.structureview.show.inherited"), null, IconLoader
      .getIcon("/hierarchy/supertypes.png"));
  }

  @NotNull
  public String getName() {
    return ID;
  }
}
