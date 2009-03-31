/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface PomTargetPsiElement {

  @NotNull PomTarget getTarget();

}
