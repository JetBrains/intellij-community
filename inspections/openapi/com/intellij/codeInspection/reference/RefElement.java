/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.Collection;

/**
 * User: anna
 * Date: 27-Dec-2005
 */
public interface RefElement extends RefEntity{
  RefModule getModule();

  boolean isValid();

  RefManager getRefManager();

  String getExternalName();

  PsiElement getElement();

  boolean isReachable();

  boolean isReferenced();

  Collection<RefElement> getOutReferences();

  Collection<RefElement> getInReferences();

  boolean isFinal();

  boolean isStatic();

  boolean isUsesDeprecatedApi();

  boolean isEntry();
  boolean isPermanentEntry();

  boolean isSyntheticJSP();

  @Nullable
  String getAccessModifier();

  URL getURL();
}
