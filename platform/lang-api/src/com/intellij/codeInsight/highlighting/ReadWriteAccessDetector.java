package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class ReadWriteAccessDetector {
  public static final ExtensionPointName<ReadWriteAccessDetector> EP_NAME = ExtensionPointName.create("com.intellij.readWriteAccessDetector");

  @Nullable
  public static ReadWriteAccessDetector findDetector(final PsiElement element) {
    ReadWriteAccessDetector detector = null;
    for(ReadWriteAccessDetector accessDetector: Extensions.getExtensions(EP_NAME)) {
      if (accessDetector.isReadWriteAccessible(element)) {
        detector = accessDetector;
        break;
      }
    }
    return detector;
  }

  public enum Access { Read, Write, ReadWrite }

  public abstract boolean isReadWriteAccessible(PsiElement element);
  public abstract boolean isDeclarationWriteAccess(PsiElement element);
  public abstract Access getReferenceAccess(final PsiElement referencedElement, PsiReference reference);
  public abstract Access getExpressionAccess(PsiElement expression);
}
