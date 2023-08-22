package com.intellij.externalSystem;

import com.intellij.buildsystem.model.DeclaredDependency;
import com.intellij.buildsystem.model.unified.UnifiedDependency;
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents API for modification of external system build script files.
 * This modificator gives bounded read/write access for build files model: dependencies, repositories, etc.
 *
 * @see DependencyModifierService
 */
@ApiStatus.Experimental
public interface ExternalDependencyModificator {
  ExtensionPointName<ExternalDependencyModificator> EP_NAME = ExtensionPointName.create("com.intellij.externalSystem.dependencyModifier");

  /**
   * Checks that {@code module}'s build system can be modified by this {@link ExternalDependencyModificator}.
   *
   * @param module is build file model identifier.
   * @see DependencyModifierService
   */
  boolean supports(@NotNull Module module);

  /**
   * Adds dependency into {@code module}'s build file and its model.
   *
   * @param module     is build file model identifier.
   * @param descriptor is dependency descriptor to append.
   * @throws IllegalArgumentException when {@code descriptor}'s data aren't full for modifying build system.
   */
  void addDependency(@NotNull Module module, @NotNull UnifiedDependency descriptor);

  /**
   * Updates dependency in {@code module}'s build file and its model.
   *
   * @param module        is build file model identifier.
   * @param oldDescriptor is dependency descriptor that should be updated.
   * @param newDescriptor is new dependency descriptor data.
   * @throws IllegalArgumentException when {@code oldDescriptor}'s or {@code newDescriptor}'s data aren't full for modifying build system.
   */
  void updateDependency(@NotNull Module module,
                        @NotNull UnifiedDependency oldDescriptor,
                        @NotNull UnifiedDependency newDescriptor);

  /**
   * Removes dependency from {@code module}'s build file and its model.
   *
   * @param module     is build file model identifier.
   * @param descriptor is dependency descriptor that should be removed.
   * @throws IllegalArgumentException when {@code descriptor}'s data aren't full for modifying build system.
   */
  void removeDependency(@NotNull Module module, @NotNull UnifiedDependency descriptor);

  /**
   * Adds repository into {@code module}'s build file and its model.
   *
   * @param module     is build file model identifier.
   * @param repository is dependency repository to append.
   */
  void addRepository(@NotNull Module module, @NotNull UnifiedDependencyRepository repository);

  /**
   * Removes repository from {@code module}'s build file and its model.
   *
   * @param module     is build file model identifier.
   * @param repository is dependency repository that should be removed.
   */
  void deleteRepository(@NotNull Module module, @NotNull UnifiedDependencyRepository repository);

  /**
   * Gets declared dependencies from build file model.
   *
   * @param module is build file model identifier.
   */
  List<DeclaredDependency> declaredDependencies(@NotNull Module module);

  /**
   * Gets declared dependency repositories from build file model.
   *
   * @param module is build file model identifier.
   */
  List<UnifiedDependencyRepository> declaredRepositories(@NotNull Module module);
}
