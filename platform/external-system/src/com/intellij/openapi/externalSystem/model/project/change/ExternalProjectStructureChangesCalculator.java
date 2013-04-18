package com.intellij.openapi.externalSystem.model.project.change;

import com.intellij.openapi.externalSystem.model.project.ProjectEntityData;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectChangesCalculationContext;
import org.jetbrains.annotations.NotNull;

/**
 * Defines common interface to the strategy that calculates difference between the corresponding external system and ide entities
 * (e.g. between an external and ide module).
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/24/12 12:15 PM
 * @param <I>   target intellij entity type
 * @param <G>   target gradle entity type
 */
public interface ExternalProjectStructureChangesCalculator<G extends ProjectEntityData, I> {

  /**
   * Calculates changes between the given entities.
   *
   * @param externalEntity    target external system entity
   * @param ideEntity         target ide entity
   * @param context           target diff calculation context
   */
  void calculate(@NotNull G externalEntity, @NotNull I ideEntity, @NotNull ExternalProjectChangesCalculationContext context);
}
