// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Aligner, which is useful when there is more than one alignment group in block
 * When one configuration requests alignment, all other ones will change current alignment strategy
 * When no configuration requests alignment, all configurations will change current alignment strategy
 */
public class CompositeAligner extends ChildAlignmentStrategyProvider {
  private final List<AlignerConfigurationWrapper> myConfigurations;

  public CompositeAligner(List<AlignerConfiguration> configuration) {
    myConfigurations = new ArrayList<>();
    for (AlignerConfiguration alignerConfiguration : configuration) {
      myConfigurations.add(new AlignerConfigurationWrapper(alignerConfiguration));
    }
  }

  @Override
  public AlignmentStrategy getNextChildStrategy(@NotNull ASTNode child) {
    int shouldAlignIndex = -1;
    for (int i = 0, size = myConfigurations.size(); i < size; i++) {
      AlignerConfigurationWrapper configuration = myConfigurations.get(i);
      if (configuration.myConfiguration.shouldAlign(child)) {
        shouldAlignIndex = i;
        break;
      }
    }
    if (shouldAlignIndex == -1) {
      updateAllStrategies();
      return AlignmentStrategy.getNullStrategy();
    }

    if (isWhiteSpaceWithBlankLines(child.getTreePrev())) {
      updateAllStrategies();
      return myConfigurations.get(shouldAlignIndex).myCurrentStrategy;
    }
    updateAllStrategiesExcept(shouldAlignIndex);
    return myConfigurations.get(shouldAlignIndex).myCurrentStrategy;
  }

  private void updateAllStrategies() {
    for (AlignerConfigurationWrapper configuration : myConfigurations) {
      configuration.update();
    }
  }

  private void updateAllStrategiesExcept(int index) {
    for (int i = 0; i < myConfigurations.size(); i++) {
      if (i == index) continue;
      AlignerConfigurationWrapper configuration = myConfigurations.get(i);
      configuration.update();
    }
  }

  /**
   * @see AlignerConfiguration#shouldAlign(ASTNode)
   * @see AlignerConfiguration#createConfiguration(Predicate, Supplier)
   */
  public static AlignerConfiguration createConfiguration(
    Predicate<ASTNode> shouldAlign,
    Supplier<? extends AlignmentStrategy> strategySupplier) {
    return new AlignerConfiguration() {
      @Override
      public boolean shouldAlign(@NotNull ASTNode child) {
        return shouldAlign.test(child);
      }

      @NotNull
      @Override
      public AlignmentStrategy createStrategy() {
        return strategySupplier.get();
      }
    };
  }

  interface AlignerConfiguration {
    /**
     * Method used to determine, whether current alignment strategy should be applied to given child
     */
    boolean shouldAlign(@NotNull ASTNode child);

    @NotNull
    AlignmentStrategy createStrategy();
  }

  /**
   * Stateful object that stores current AlignmentStrategy for AlignmentConfiguration
   */
  private static class AlignerConfigurationWrapper {
    private final AlignerConfiguration myConfiguration;
    private AlignmentStrategy myCurrentStrategy;

    private AlignerConfigurationWrapper(AlignerConfiguration configuration) {
      myConfiguration = configuration;
      myCurrentStrategy = configuration.createStrategy();
    }

    void update() {
      myCurrentStrategy = myConfiguration.createStrategy();
    }
  }
}
