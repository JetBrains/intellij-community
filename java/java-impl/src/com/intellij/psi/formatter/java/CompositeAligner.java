// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
    AlignerConfigurationWrapper currentAlignmentWrapper = null;
    for (AlignerConfigurationWrapper configuration : myConfigurations) {
      if (configuration.myConfiguration.shouldAlign(child)) {
        currentAlignmentWrapper = configuration;
        break;
      }
    }

    AlignerConfigurationWrapper wrapperWithStrategyToPreserve = isWhiteSpaceWithBlankLines(child.getTreePrev()) ? null : currentAlignmentWrapper;
    updateAllStrategiesExcept(wrapperWithStrategyToPreserve);
    return currentAlignmentWrapper == null
           ? AlignmentStrategy.getNullStrategy()
           : currentAlignmentWrapper.myCurrentStrategy;
  }

  private void updateAllStrategiesExcept(@Nullable AlignerConfigurationWrapper wrapper) {
    for (AlignerConfigurationWrapper configuration : myConfigurations) {
      if (configuration == wrapper) continue;
      configuration.update();
    }
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
  private static final class AlignerConfigurationWrapper {
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
