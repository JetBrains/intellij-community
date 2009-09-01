package com.intellij.conversion;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ConverterProvider {
  public static final ExtensionPointName<ConverterProvider> EP_NAME = ExtensionPointName.create("com.intellij.project.converterProvider");
  private final String myId;

  protected ConverterProvider(@NotNull @NonNls String id) {
    myId = id;
  }

  public String[] getPrecedingConverterIds() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public final String getId() {
    return myId;
  }

  @NotNull
  public abstract String getConversionDescription();

  @NotNull
  public abstract ProjectConverter createConverter(@NotNull ConversionContext context); 
}
