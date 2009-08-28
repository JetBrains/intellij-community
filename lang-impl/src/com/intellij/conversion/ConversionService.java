package com.intellij.conversion;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ConversionService {

  public static ConversionService getInstance() {
    return ServiceManager.getService(ConversionService.class);
  }

  public abstract boolean convertSilently(@NotNull String projectPath, @NotNull ConversionListener conversionListener);

  public abstract boolean convert(@NotNull String projectPath);

}
