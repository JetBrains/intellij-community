package com.intellij.dupLocator;

import com.intellij.openapi.util.JDOMExternalizable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public interface ExternalizableDuplocatorState extends DuplocatorState, JDOMExternalizable {

  boolean distinguishRole(@NotNull PsiElementRole role);

  boolean distinguishLiterals();
}
