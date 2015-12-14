/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.util.treeView;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.Nullable;

/**
 * Used by UI trees to get a more memory-efficient representation of their user objects.
 * For example, instead of holding PsiElement's they can hold PsiAnchor's which don't hold AST, document text, etc.
 * This service is used to perform object<->anchor conversion automatically so that all 100500 tree nodes don't have to do this themselves.
 * 
 * @author peter
 */
public class TreeAnchorizer {
  private static final TreeAnchorizer ourInstance;
  static {
    TreeAnchorizer implementation = ServiceManager.getService(TreeAnchorizer.class);
    ourInstance = implementation == null ? new TreeAnchorizer() : implementation;
  }

  public static TreeAnchorizer getService() {
    return ourInstance;
  }

  public Object createAnchor(Object element) {
    return element;
  }

  @Nullable
  public Object retrieveElement(Object anchor) {
    return anchor;
  }

  public void freeAnchor(Object element) { }
}
