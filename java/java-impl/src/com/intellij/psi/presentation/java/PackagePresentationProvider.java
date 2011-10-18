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
package com.intellij.psi.presentation.java;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProvider;
import com.intellij.psi.PsiPackage;
import com.intellij.util.PlatformIcons;

public class PackagePresentationProvider implements ItemPresentationProvider<PsiPackage> {
  @Override
  public ItemPresentation getPresentation(final PsiPackage aPackage) {
    return new PresentationData(aPackage.getName(), aPackage.getQualifiedName(), PlatformIcons.PACKAGE_OPEN_ICON, PlatformIcons.PACKAGE_ICON, null);
  }
}
