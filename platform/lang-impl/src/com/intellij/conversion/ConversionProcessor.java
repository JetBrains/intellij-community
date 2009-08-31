package com.intellij.conversion;

/**
 * @author nik
 */
public abstract class ConversionProcessor<Settings extends ComponentManagerSettings> {
  public abstract boolean isConversionNeeded(Settings settings);

  public void preProcess(Settings settings) throws CannotConvertException {
  }

  public abstract void process(Settings settings) throws CannotConvertException ;

  public void postProcess(Settings settings) throws CannotConvertException {
  }
}
