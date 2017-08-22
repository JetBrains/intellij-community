/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;

/**
 * @author mike
 */
public class PsiInvalidElementAccessException extends RuntimeException implements ExceptionWithAttachments {
  private static final Key<Object> INVALIDATION_TRACE = Key.create("INVALIDATION_TRACE");
  private static final Key<Boolean> REPORTING_EXCEPTION = Key.create("REPORTING_EXCEPTION");

  private final SoftReference<PsiElement> myElementReference;  // to prevent leaks, since exceptions are stored in IdeaLogger
  private final Attachment[] myDiagnostic;
  private final String myMessage;

  public PsiInvalidElementAccessException(@Nullable PsiElement element) {
    this(element, null, null);
  }

  public PsiInvalidElementAccessException(@Nullable PsiElement element, @Nullable String message) {
    this(element, message, null);
  }

  public PsiInvalidElementAccessException(@Nullable PsiElement element, @Nullable Throwable cause) {
    this(element, null, cause);
  }

  public PsiInvalidElementAccessException(@Nullable PsiElement element, @Nullable String message, @Nullable Throwable cause) {
    super(null, cause);
    myElementReference = new SoftReference<>(element);

    if (element == null) {
      myMessage = message;
      myDiagnostic = Attachment.EMPTY_ARRAY;
    }
    else if (element == PsiUtilCore.NULL_PSI_ELEMENT) {
      myMessage = "NULL_PSI_ELEMENT ;" + message;
      myDiagnostic = Attachment.EMPTY_ARRAY;
    }
    else {
      boolean recursiveInvocation = Boolean.TRUE.equals(element.getUserData(REPORTING_EXCEPTION));
      element.putUserData(REPORTING_EXCEPTION, Boolean.TRUE);

      try {
        Object trace = recursiveInvocation ? null : getPsiInvalidationTrace(element);
        myMessage = getMessageWithReason(element, message, recursiveInvocation, trace);
        myDiagnostic = createAttachments(trace);
      }
      finally {
        element.putUserData(REPORTING_EXCEPTION, null);
      }
    }
  }

  private PsiInvalidElementAccessException(@NotNull ASTNode node, @Nullable String message) {
    myElementReference = new SoftReference<>(null);
    final IElementType elementType = node.getElementType();
    myMessage = "Element " + node.getClass() + " of type " + elementType + " (" + elementType.getClass() + ")" +
                (message == null ? "" : "; " + message);
    myDiagnostic = createAttachments(findInvalidationTrace(node));
  }

  public static PsiInvalidElementAccessException createByNode(@NotNull ASTNode node, @Nullable String message) {
    return new PsiInvalidElementAccessException(node, message);
  }

  @NotNull
  private static Attachment[] createAttachments(@Nullable Object trace) {
    return trace == null
           ? Attachment.EMPTY_ARRAY
           : new Attachment[]{trace instanceof Throwable ? new Attachment("invalidation", (Throwable)trace)
                                                         : new Attachment("diagnostic.txt", trace.toString())};
  }

  @Nullable
  private static Object getPsiInvalidationTrace(@NotNull PsiElement element) {
    Object trace = getInvalidationTrace(element);
    if (trace != null) return trace;
    
    if (element instanceof PsiFile) {
      return getInvalidationTrace(((PsiFile)element).getOriginalFile());
    }
    return findInvalidationTrace(element.getNode());
  }

  private static String getMessageWithReason(@NotNull PsiElement element,
                                             @Nullable String message,
                                             boolean recursiveInvocation,
                                             @Nullable Object trace) {
    String reason = "Element: " + element.getClass();
    if (!recursiveInvocation) {
      String traceText = !isTrackingInvalidation() ? "disabled" :
                         trace != null ? "see attachment" :
                         "no info";
      try {
        reason += " because: " + findOutInvalidationReason(element);
      }
      catch (PsiInvalidElementAccessException ignore) {
      }
      reason += "\ninvalidated at: " + traceText;
    }
    return reason + (message == null ? "" : "; " + message);
  }

  @Override
  public String getMessage() {
    return myMessage;
  }

  @NotNull
  @Override
  public Attachment[] getAttachments() {
    return myDiagnostic;
  }

  public static Object findInvalidationTrace(@Nullable ASTNode element) {
    while (element != null) {
      Object trace = element.getUserData(INVALIDATION_TRACE);
      if (trace != null) {
        return trace;
      }
      ASTNode parent = element.getTreeParent();
      if (parent == null && element instanceof FileASTNode) {
        PsiElement psi = element.getPsi();
        trace = psi == null ? null : psi.getUserData(INVALIDATION_TRACE);
        if (trace != null) {
          return trace;
        }
      }
      element = parent;
    }
    return null;
  }

  @NonNls
  @NotNull
  public static String findOutInvalidationReason(@NotNull PsiElement root) {
    if (root == PsiUtilCore.NULL_PSI_ELEMENT) return "NULL_PSI_ELEMENT";

    PsiElement element = root instanceof PsiFile ? root : root.getParent();
    if (element == null) {
      String m = "parent is null";
      if (root instanceof StubBasedPsiElement) {
        StubElement stub = ((StubBasedPsiElement)root).getStub();
        while (stub != null) {
          //noinspection StringConcatenationInLoop
          m += "\n  each stub=" + stub;
          if (stub instanceof PsiFileStub) {
            m += "; fileStub.psi=" + stub.getPsi() + "; reason=" + ((PsiFileStub)stub).getInvalidationReason();
          }
          stub = stub.getParentStub();
        }
      }
      return m;
    }

    while (element != null && !(element instanceof PsiFile)) element = element.getParent();
    PsiFile file = (PsiFile)element;
    if (file == null) return "containing file is null";

    FileViewProvider provider = file.getViewProvider();
    VirtualFile vFile = provider.getVirtualFile();
    if (!vFile.isValid()) return vFile + " is invalid";
    if (!provider.isPhysical()) {
      PsiElement context = file.getContext();
      if (context != null && !context.isValid()) {
        return "invalid context: " + findOutInvalidationReason(context);
      }
    }

    PsiFile original = file.getOriginalFile();
    if (original != file && !original.isValid()) {
      return "invalid original: " + findOutInvalidationReason(original);
    }

    PsiManager manager = file.getManager();
    if (manager.getProject().isDisposed()) return "project is disposed";

    Language language = file.getLanguage();
    if (language != provider.getBaseLanguage()) return "File language:" + language + " != Provider base language:" + provider.getBaseLanguage();

    FileViewProvider p = manager.findViewProvider(vFile);
    if (provider != p) return "different providers: " + provider + "(" + id(provider) + "); " + p + "(" + id(p) + ")";

    if (!provider.isPhysical()) return "non-physical provider: " + provider; // "dummy" file?

    return "psi is outdated";
  }

  private static String id(FileViewProvider provider) {
    return Integer.toHexString(System.identityHashCode(provider));
  }

  public static void setInvalidationTrace(@NotNull UserDataHolder element, Object trace) {
    element.putUserData(INVALIDATION_TRACE, trace);
  }

  public static Object getInvalidationTrace(@NotNull UserDataHolder element) {
    return element.getUserData(INVALIDATION_TRACE);
  }

  public static boolean isTrackingInvalidation() {
    return Registry.is("psi.track.invalidation");
  }

  @Nullable
  public PsiElement getPsiElement() {
    return myElementReference.get();
  }
}
