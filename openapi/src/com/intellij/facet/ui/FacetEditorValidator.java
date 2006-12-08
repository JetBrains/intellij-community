/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.ui;

import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author nik
 */
public abstract class FacetEditorValidator {
  public abstract void check() throws ConfigurationException;
}
