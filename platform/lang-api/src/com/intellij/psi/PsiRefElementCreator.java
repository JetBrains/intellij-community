/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public interface PsiRefElementCreator<Parent, Child> {

  @NotNull
  Child createChild(@NotNull Parent parent) throws IncorrectOperationException;

}
