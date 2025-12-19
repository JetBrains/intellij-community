// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.Arrays;

public final class PsiInvalidElementAccessException extends RuntimeException implements ExceptionWithAttachments {
  private static final Key<Object> INVALIDATION_TRACE = Key.create("INVALIDATION_TRACE");
  private static final Key<Object> CREATION_TRACE = Key.create("CREATION_TRACE");
  private static final Key<Boolean> REPORTING_EXCEPTION = Key.create("REPORTING_EXCEPTION");

  private final SoftReference<PsiElement> myElementReference;  // to prevent leaks, since exceptions are stored in IdeaLogger
  private final Attachment[] myDiagnostic;
  private final @NonNls String myMessage;

  public PsiInvalidElementAccessException(@Nullable PsiElement element) {
    this(element, null, null);
  }

  public PsiInvalidElementAccessException(@Nullable PsiElement element, @Nullable @NonNls String message) {
    this(element, message, null);
  }

  public PsiInvalidElementAccessException(@Nullable PsiElement element, @Nullable Throwable cause) {
    this(element, null, cause);
  }

  public PsiInvalidElementAccessException(@Nullable PsiElement element, @Nullable @NonNls String message, @Nullable Throwable cause) {
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
        Object invalidationTrace = recursiveInvocation ? null : getPsiInvalidationTrace(element);
        Object creationTrace = findCreationTrace(element);
        myMessage = getMessageWithReason(element, message, recursiveInvocation, invalidationTrace);
        myDiagnostic = createAttachments(invalidationTrace, creationTrace);
      }
      finally {
        element.putUserData(REPORTING_EXCEPTION, null);
      }
    }
  }

  private PsiInvalidElementAccessException(@NotNull ASTNode node, @Nullable @NonNls String message) {
    myElementReference = new SoftReference<>(null);
    final IElementType elementType = node.getElementType();
    myMessage = "Element " + node.getClass() + " of type " + elementType + " (" + elementType.getClass() + ")" +
                (message == null ? "" : "; " + message);
    myDiagnostic = createAttachments(findInvalidationTrace(node), findCreationTrace(node));
  }

  public static @NotNull PsiInvalidElementAccessException createByNode(@NotNull ASTNode node, @Nullable @NonNls String message) {
    return new PsiInvalidElementAccessException(node, message);
  }

  private static Attachment @NotNull [] createAttachments(@Nullable Object invalidationTrace, @Nullable Object creationTrace) {
    Attachment invalidationAttachment = traceAsAttachment("invalidation", "diagnostics.txt", invalidationTrace);
    Attachment creationAttachment = traceAsAttachment("creation_trace.txt", "creation_diagnostics.txt", creationTrace);
    return ContainerUtil.notNullize(Arrays.asList(invalidationAttachment, creationAttachment)).toArray(Attachment.EMPTY_ARRAY);
  }

  private static @Nullable Attachment traceAsAttachment(@NotNull String nameForThrowable, @NotNull String nameForString, @Nullable Object trace) {
    if (trace instanceof Throwable) {
      return new Attachment(nameForThrowable, (Throwable)trace);
    }
    if (trace != null) {
      return new Attachment(nameForString, trace.toString());
    }
    return null;
  }

  private static @Nullable Object findCreationTrace(@Nullable PsiElement element) {
    if (element == null) return null;
    if (element instanceof PsiFile) {
      // don't try element.getNode() because this can trigger lazy file tree building
      return findFileCreationTrace((PsiFile)element);
    }

    return RecursionManager.doPreventingRecursion(element, false, () ->
      // StubBasedPsiElementBase causes SOE on calling #getNode
      findCreationTrace(element.getNode())
    );
  }

  private static @Nullable Object findCreationTrace(@Nullable ASTNode node) {
    while (node != null) {
      Object astTrace = getCreationTrace(node);
      if (astTrace != null) {
        return astTrace;
      }

      PsiElement psi = node.getPsi();
      if (psi != null) {
        Object psiTrace = getCreationTrace(psi);
        if (psiTrace != null) {
          return psiTrace;
        }
      }

      if (node instanceof FileASTNode) {
        PsiElement filePsi = node.getPsi();
        if (filePsi instanceof PsiFile) {
          Object fileCreationTrace = findFileCreationTrace((PsiFile)filePsi);
          if (fileCreationTrace != null) {
            return fileCreationTrace;
          }
        }

        return null;
      }
      node = node.getTreeParent();
    }
    return null;
  }

  private static @Nullable Object findFileCreationTrace(@NotNull PsiFile filePsi) {
    VirtualFile virtualFile = filePsi.getViewProvider().getVirtualFile();
    Object vfileCreationTrace = getCreationTrace(virtualFile);
    if (vfileCreationTrace != null) {
      return vfileCreationTrace;
    }
    return null;
  }

  private static @Nullable Object getPsiInvalidationTrace(@NotNull PsiElement element) {
    Object trace = getInvalidationTrace(element);
    if (trace != null) return trace;

    if (element instanceof PsiFile) {
      return getInvalidationTrace(((PsiFile)element).getOriginalFile());
    }
    return findInvalidationTrace(element.getNode());
  }

  private static @NotNull String getMessageWithReason(@NotNull PsiElement element,
                                                      @Nullable String message,
                                                      boolean recursiveInvocation,
                                                      @Nullable Object trace) {
    @NonNls String reason = "Invalid PSI Element: " + element.getClass();
    if (!recursiveInvocation) {
      try {
        reason += " #" + getLanguage(element).getID() + " ";
      }
      catch (PsiInvalidElementAccessException ignore) { }
      String traceText = !isTrackingInvalidation() ? "disabled" :
                         trace != null ? "see attachment" :
                         "no info";
      try {
        reason += " because: " + findOutInvalidationReason(element);
      }
      catch (PsiInvalidElementAccessException ignore) { }
      reason += "\ninvalidated at: " + traceText;
    }
    return reason + (message == null ? "" : "; " + message);
  }

  private static @NotNull Language getLanguage(@NotNull PsiElement element) {
    return element instanceof ASTNode ? ((ASTNode)element).getElementType().getLanguage() : element.getLanguage();
  }

  @Override
  public String getMessage() {
    return myMessage;
  }

  @Override
  public Attachment @NotNull [] getAttachments() {
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

  public static @NonNls @NotNull String findOutInvalidationReason(@NotNull PsiElement root) {
    if (root == PsiUtilCore.NULL_PSI_ELEMENT) {
      return "NULL_PSI_ELEMENT";
    }

    PsiElement lastParent = root;
    PsiElement element = root instanceof PsiFile ? root : root.getParent();
    if (element == null) {
      @NonNls String m = "parent is null";
      if (root instanceof StubBasedPsiElement) {
        StubElement<?> stub = ((StubBasedPsiElement<?>)root).getStub();
        while (stub != null) {
          //noinspection StringConcatenationInLoop
          m += "\n  each stub=" + stub;
          if (stub instanceof PsiFileStub) {
            m += "; fileStub.psi=" + stub.getPsi() + "; reason=" + ((PsiFileStub<?>)stub).getInvalidationReason();
          }
          stub = stub.getParentStub();
        }
      }
      return m;
    }

    String hierarchy = "";
    while (element != null && !(element instanceof PsiFile)) {
      //noinspection StringConcatenationInLoop
      hierarchy += (hierarchy.isEmpty() ? "" : ", ") + element.getClass();
      lastParent = element;
      element = element.getParent();
    }
    PsiFile psiFile = (PsiFile)element;
    if (psiFile == null) {
      PsiElement context = lastParent.getContext();
      return "containing file is null; hierarchy=" + hierarchy +
             ", context=" + context +
             ", contextFile=" + JBIterable.generate(context, PsiElement::getParent).find(e -> e instanceof PsiFile);
    }

    FileViewProvider provider = psiFile.getViewProvider();
    VirtualFile vFile = provider.getVirtualFile();
    if (!vFile.isValid()) {
      return vFile + " is invalid";
    }
    if (!provider.isPhysical()) {
      PsiElement context = psiFile.getContext();
      if (context != null && !context.isValid()) {
        return "invalid context: " + findOutInvalidationReason(context);
      }
    }

    PsiFile original = psiFile.getOriginalFile();
    if (original != psiFile && !original.isValid()) {
      return "invalid original: " + findOutInvalidationReason(original);
    }

    PsiManager manager = psiFile.getManager();
    if (manager.getProject().isDisposed()) {
      return "project is disposed: " + manager.getProject();
    }

    Language language = psiFile.getLanguage();
    if (language != provider.getBaseLanguage()) {
      return "File language:" + language + " != Provider base language:" + provider.getBaseLanguage();
    }

    FileViewProvider p = manager.findCachedViewProvider(vFile);
    if (provider != p) {
      return "different providers: " + provider + "(" + id(provider) + "); " + p + "(" + id(p) + ")";
    }

    if (!provider.isPhysical()) {
      return "non-physical provider: " + provider; // "dummy" file?
    }

    return "psi is outdated";
  }

  private static @NotNull String id(@Nullable FileViewProvider provider) {
    return Integer.toHexString(System.identityHashCode(provider));
  }

  public static void setInvalidationTrace(@NotNull UserDataHolder element, Object trace) {
    element.putUserData(INVALIDATION_TRACE, trace);
  }

  public static Object getInvalidationTrace(@NotNull UserDataHolder element) {
    return element.getUserData(INVALIDATION_TRACE);
  }

  public static @Nullable Object getCreationTrace(@NotNull UserDataHolder element) {
    return element.getUserData(CREATION_TRACE);
  }

  public static void setCreationTrace(@NotNull UserDataHolderBase userDataHolder, @NotNull Object trace) {
    userDataHolder.putUserData(CREATION_TRACE, trace);
  }

  public static boolean isTrackingInvalidation() {
    return Registry.is("psi.track.invalidation", true);
  }

  public @Nullable PsiElement getPsiElement() {
    return myElementReference.get();
  }
}
