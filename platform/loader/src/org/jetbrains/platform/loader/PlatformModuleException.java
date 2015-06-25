package org.jetbrains.platform.loader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.platform.loader.repository.RuntimeModuleDescriptor;

/**
 * @author nik
 */
public class PlatformModuleException extends RuntimeException {
  private RuntimeModuleDescriptor myModule;

  public PlatformModuleException(Throwable cause, @NotNull RuntimeModuleDescriptor module) {
    super(cause);
    myModule = module;
  }

  public PlatformModuleException(String message, @NotNull RuntimeModuleDescriptor module) {
    super(message);
    myModule = module;
  }

  public PlatformModuleException(String message, Throwable cause, @NotNull RuntimeModuleDescriptor module) {
    super(message, cause);
    myModule = module;
  }
}
