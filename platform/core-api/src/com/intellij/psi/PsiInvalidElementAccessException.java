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

package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;

/**
 * @author mike
 */
public class PsiInvalidElementAccessException extends RuntimeException {
  private final SoftReference<PsiElement> myElementReference;  // to prevent leaks, since exceptions are stored in IdeaLogger

  public PsiInvalidElementAccessException(PsiElement element) {
    this(element, (String)null);
  }

  public PsiInvalidElementAccessException(PsiElement element, @Nullable String message) {
    this(element, getMessageWithReason(element, message), null);
  }

  public PsiInvalidElementAccessException(PsiElement element, @Nullable Throwable cause) {
    this(element, getMessageWithReason(element, null), cause);
  }

  public PsiInvalidElementAccessException(PsiElement element, @Nullable String message, @Nullable Throwable cause) {
    super(message, cause);
    myElementReference = new SoftReference<PsiElement>(element);
  }

  private static String getMessageWithReason(@Nullable PsiElement element, @Nullable String message) {
    return (element == null ? "Unknown psi element" : "Element: " + element.getClass() + " because: " + reason(element)) +
          (message == null ? "" : "; " + message);
  }

  @NonNls
  @NotNull
  private static String reason(@NotNull PsiElement root){
    if (root == PsiUtilCore.NULL_PSI_ELEMENT) return "NULL_PSI_ELEMENT";
    PsiElement element = root instanceof PsiFile ? root : root.getParent();
    if (element == null) return "parent is null";
    while (element != null && !(element instanceof PsiFile) && element.getParent() != null) {
      element = element.getParent();
    }
    PsiFile file = element instanceof PsiFile ? (PsiFile)element : null;
    if (file == null) return "containing file is null";
    FileViewProvider provider = file.getViewProvider();
    VirtualFile vFile = provider.getVirtualFile();
    if (!vFile.isValid()) return vFile+" is invalid";
    if (!provider.isPhysical()) {
      PsiElement context = file.getContext();
      if (context != null && !context.isValid()) {
        return "invalid context: " + reason(context);
      }
      return "non-physical provider: " + provider; // "dummy" file?
    }
    PsiManager manager = file.getManager();
    if (manager.getProject().isDisposed()) return "project is disposed";
    Language language = file.getLanguage();
    if (language != provider.getBaseLanguage()) return "File language:"+language+" != Provider base language:"+provider.getBaseLanguage();

    FileViewProvider provider1 = manager.findViewProvider(vFile);
    if (provider != provider1) return "different providers: "+provider+"("+Integer.toHexString(System.identityHashCode(provider))+"); "+provider1+"("+Integer.toHexString(System.identityHashCode(provider1))+")";
    return "psi is outdated";
  }

  @Nullable
  public PsiElement getPsiElement() {
    return myElementReference.get();
  }
}
