package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface SmartPointerElementInfoFactory {
  ExtensionPointName<SmartPointerElementInfoFactory> EP_NAME = ExtensionPointName.create("com.intellij.smartPointerElementInfoFactory");

  @Nullable
  SmartPointerElementInfo createElementInfo(PsiElement element);
}
