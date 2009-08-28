package com.intellij.conversion;

/**
 * @author nik
 */
public abstract class ConversionProcessor<Settings extends ComponentManagerSettings> {
  public abstract boolean isConversionNeeded(Settings settings); 

  public abstract void preProcess(Settings settings);

  public abstract void process(Settings settings);

  public abstract void postProcess(Settings settings);
}
