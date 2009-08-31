/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.projectWizard;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.roots.libraries.Library;

/**
 * @author nik
 */
public interface LibraryFilter {

  boolean accept(@NotNull Library library);

}
