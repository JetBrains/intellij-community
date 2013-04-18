package com.intellij.openapi.externalSystem.util;

import com.intellij.openapi.externalSystem.service.project.ModuleAwareContentRoot;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;

/**
 * Dispatch callback for intellij project entities.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/14/12 1:48 PM
 */
public interface IdeEntityVisitor {

  void visit(@NotNull Project project);

  void visit(@NotNull Module module);

  void visit(@NotNull ModuleAwareContentRoot contentRoot);
  
  void visit(@NotNull LibraryOrderEntry libraryDependency);

  void visit(@NotNull ModuleOrderEntry moduleDependency);
  
  void visit(@NotNull Library library);
}
