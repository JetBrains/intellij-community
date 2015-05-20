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
package com.intellij.pom;

/**
 * Represents an instance which can be shown in the IDE (e.g. a file, a specific location inside a file, etc).
 * <p/>
 * Many {@link com.intellij.psi.PsiElement}s implement this interface (see {@link com.intellij.psi.NavigatablePsiElement}). To create an
 * instance which opens a file in editor and put caret to a specific location use {@link com.intellij.openapi.fileEditor.OpenFileDescriptor}.
 */
public interface Navigatable {
  /**
   * Open editor and select/navigate to the object there if possible.
   * Just do nothing if navigation is not possible like in case of a package
   *
   * @param requestFocus <code>true</code> if focus requesting is necessary
   */
  void navigate(boolean requestFocus);

  /**
   * @return <code>false</code> if navigation is not possible for any reason.
   */
  boolean canNavigate();

  /**
   * @return <code>false</code> if navigation to source is not possible for any reason.
   * Source means some kind of editor
   */
  boolean canNavigateToSource();
}
