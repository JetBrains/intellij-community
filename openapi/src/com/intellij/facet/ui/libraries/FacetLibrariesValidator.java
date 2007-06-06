/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.ui.libraries;

import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.Facet;

/**
 * @author nik
 */
public abstract class FacetLibrariesValidator extends FacetEditorValidator {

  public abstract void setRequiredLibraries(LibraryInfo[] requiredLibraries);

  public abstract FacetLibrariesValidatorDescription getDescription();

  public abstract void onFacetInitialized(Facet facet);

}
