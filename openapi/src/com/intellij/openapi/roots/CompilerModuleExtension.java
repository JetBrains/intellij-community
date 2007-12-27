/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public abstract class CompilerModuleExtension extends ModuleExtension {
  @NonNls public static final String PRODUCTION = "production";
  @NonNls public static final String TEST = "test";

  public static CompilerModuleExtension getInstance(final Module module) {
    final ModuleExtension[] extensions = Extensions.getExtensions(EP_NAME, module);
    for (ModuleExtension extension : extensions) {
      if (CompilerModuleExtension.class.isAssignableFrom(extension.getClass())) return (CompilerModuleExtension)extension;
    }
    return null;
  }

  /**
   * Returns a compiler output path for production sources of the module, if it is valid.
   *
   * @return the compile output path, or null if one is not valid.
   */

  @Nullable
  public abstract VirtualFile getCompilerOutputPath();

  public abstract void setCompilerOutputPath(VirtualFile file);

  /**
   * Returns a compiler output path url for production sources of the module.
   *
   * @return the compiler output path URL, or null if it has never been set.
   */
  @Nullable
  public abstract String getCompilerOutputUrl();

  public abstract void setCompilerOutputPath(String url);



   /**
   * Returns a compiler output path for test sources of the module, if it is valid.
   *
   * @return the compile output path for the test sources, or null if one is not valid.
   */
  @Nullable
  public abstract VirtualFile getCompilerOutputPathForTests();

  public abstract void setCompilerOutputPathForTests(VirtualFile file);

  /**
   * Returns a compiler output path url for test sources of the module.
   *
   * @return the compiler output path URL, or null if it has never been set.
   */
  @Nullable
  public abstract String getCompilerOutputUrlForTests();

  public abstract void setCompilerOutputPathForTests(String url);


   /**
   * Makes this module inheriting compiler output from its project
   * @param inherit wether or not compiler output is inherited
   */
  public abstract void inheritCompilerOutputPath(boolean inherit);

  /**
   * Returns <code>true</code> if compiler output for this module is inherited from a project
   * @return true if compiler output path is inherited, false otherwise
   */
  public abstract boolean isCompilerOutputPathInherited();

  public abstract VirtualFilePointer getCompilerOutputPointer();

  public abstract VirtualFilePointer getCompilerOutputForTestsPointer();
}