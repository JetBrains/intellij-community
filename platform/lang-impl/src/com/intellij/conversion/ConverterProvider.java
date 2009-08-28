package com.intellij.conversion;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ConverterProvider {
  public static final ExtensionPointName<ConverterProvider> EP_NAME = ExtensionPointName.create("com.intellij.project.converterProvider");
  private final String myId;
  private final String myConversionDescription;

  protected ConverterProvider(@NotNull @NonNls String id, @NotNull String conversionDescription) {
    myId = id;
    myConversionDescription = conversionDescription;
  }

  public final String getId() {
    return myId;
  }

  public String getConversionDescription() {
    return myConversionDescription;
  }

  @NotNull
  public abstract ProjectConverter createConverter(); 
}
