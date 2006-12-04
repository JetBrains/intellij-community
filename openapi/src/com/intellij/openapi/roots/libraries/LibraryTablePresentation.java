/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots.libraries;

/**
 * @author peter
 */
public abstract class LibraryTablePresentation {

  public abstract String getDisplayName(boolean plural);

  public abstract String getDescription();

  public abstract String getLibraryTableEditorTitle();

}
