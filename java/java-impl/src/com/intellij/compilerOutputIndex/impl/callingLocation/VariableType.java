package com.intellij.compilerOutputIndex.impl.callingLocation;

import com.intellij.util.io.EnumDataDescriptor;
import com.intellij.util.io.KeyDescriptor;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public enum VariableType {
  FIELD,
  METHOD_PARAMETER,
  OTHER;

  public static final KeyDescriptor<VariableType> KEY_DESCRIPTOR = new EnumDataDescriptor<VariableType>(VariableType.class);
}
