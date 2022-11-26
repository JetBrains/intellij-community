package com.intellij.dupLocator;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public final class DefaultDuplocatorState implements ExternalizableDuplocatorState {
  private static final Logger LOG = Logger.getInstance(DefaultDuplocatorState.class);

  public boolean DISTINGUISH_VARIABLES = false;
  public boolean DISTINGUISH_FUNCTIONS = true;
  public boolean DISTINGUISH_LITERALS = true;
  public int LOWER_BOUND = 10;
  public int DISCARD_COST = 0;

  @Override
  public boolean distinguishRole(@NotNull PsiElementRole role) {
    return switch (role) {
      case VARIABLE_NAME, FIELD_NAME -> DISTINGUISH_VARIABLES;
      case FUNCTION_NAME -> DISTINGUISH_FUNCTIONS;
    };
  }

  @Override
  public boolean distinguishLiterals() {
    return DISTINGUISH_LITERALS;
  }

  @Override
  public int getLowerBound() {
    return LOWER_BOUND;
  }

  @Override
  public int getDiscardCost() {
    return DISCARD_COST;
  }
}
