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

import com.intellij.ide.util.FileStructureNodeProvider;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.AnonymousElementProvider;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class JavaAnonymousClassesNodeProvider implements FileStructureNodeProvider<JavaAnonymousClassTreeElement> {
  public static final String ID = "SHOW_ANONYMOUS";

  @Override
  public Collection<JavaAnonymousClassTreeElement> provideNodes(TreeElement node) {
    if (node instanceof JavaClassTreeElement) {
      final PsiClass cls = ((JavaClassTreeElement)node).getElement();
      for (AnonymousElementProvider provider : Extensions.getExtensions(AnonymousElementProvider.EP_NAME)) {
        final PsiElement[] elements = provider.getAnonymousElements(cls);
        if (elements != null && elements.length > 0) {
          List<JavaAnonymousClassTreeElement> result = new ArrayList<JavaAnonymousClassTreeElement>(elements.length);
          for (PsiElement element : elements) {
            result.add(new JavaAnonymousClassTreeElement((PsiAnonymousClass)element));
          }
          return result;
        }
      }
    }
    return Collections.emptyList();
  }

  @Override
  public String getCheckBoxText() {
    return "Show Anonymous Classes";
  }

  @Override
  public Shortcut[] getShortcut() {
    return new Shortcut[]{KeyboardShortcut.fromString(SystemInfo.isMac ? "meta A" : "control A")};
  }

  @NotNull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(getCheckBoxText(), null, PlatformIcons.ANONYMOUS_CLASS_ICON);
  }

  @NotNull
  @Override
  public String getName() {
    return ID;
  }
}
