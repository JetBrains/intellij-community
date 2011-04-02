
package com.intellij.ide.util.frameworkSupport;

import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class FrameworkSupportUtil {
  private FrameworkSupportUtil() {
  }

  public static List<FrameworkSupportProvider> getProviders(@NotNull ModuleType moduleType) {
    return getProviders(moduleType, null);
  }

  public static List<FrameworkSupportProvider> getProviders(@NotNull Module module) {
    return getProviders(module.getModuleType(), module);
  }

  private static List<FrameworkSupportProvider> getProviders(@NotNull ModuleType moduleType, @Nullable Module module) {
    FrameworkSupportProvider[] providers = Extensions.getExtensions(FrameworkSupportProvider.EXTENSION_POINT);
    ArrayList<FrameworkSupportProvider> result = new ArrayList<FrameworkSupportProvider>();
    for (FrameworkSupportProvider provider : providers) {
      if (provider.isEnabledForModuleType(moduleType) && (module == null || !provider.isSupportAlreadyAdded(module))) {
        result.add(provider);
      }
    }
    return result;
  }

  public static List<FrameworkSupportProvider> getProviders(@NotNull ModuleBuilder builder) {
    ArrayList<FrameworkSupportProvider> result = new ArrayList<FrameworkSupportProvider>();
    for (FrameworkSupportProvider provider : Extensions.getExtensions(FrameworkSupportProvider.EXTENSION_POINT)) {
      if (provider.isEnabledForModuleBuilder(builder)) {
        result.add(provider);
      }
    }
    return result;
  }

  public static boolean hasProviders(final Module module) {
    List<FrameworkSupportProvider> providers = getProviders(module);
    for (FrameworkSupportProvider provider : providers) {
      if (provider.getUnderlyingFrameworkId() == null) {
        return true;
      }
    }
    return false;
  }
}
