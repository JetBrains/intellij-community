package com.intellij.execution.configurations;

/**
 * User: anna
 * Date: Jan 24, 2005
 */
public interface LocatableConfiguration extends RunProfile{
  boolean isGeneratedName();

  String suggestedName();

}
