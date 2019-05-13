package com.intellij.dupLocator;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class DefaultDuplocatorState implements ExternalizableDuplocatorState {
  private static final Logger LOG = Logger.getInstance("#com.intellij.dupLocator.DefaultDuplocatorState");

  public boolean DISTINGUISH_VARIABLES = false;
  public boolean DISTINGUISH_FUNCTIONS = true;
  public boolean DISTINGUISH_LITERALS = true;
  public int LOWER_BOUND = 10;
  public int DISCARD_COST = 0;

  @Override
  public boolean distinguishRole(@NotNull PsiElementRole role) {
    switch (role) {
      case VARIABLE_NAME:
        return DISTINGUISH_VARIABLES;

      case FIELD_NAME:
        return DISTINGUISH_VARIABLES;

      case FUNCTION_NAME:
        return DISTINGUISH_FUNCTIONS;

      default:
        LOG.error("Unknown role " + role);
        return true;
    }
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
