package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface PropertyFailure<T> {
  @NotNull
  CounterExample<T> getFirstCounterExample();

  @NotNull
  CounterExample<T> getMinimalCounterexample();

  @Nullable
  Throwable getStoppingReason();
  
  int getTotalMinimizationExampleCount();
  
  int getMinimizationStageCount();
  
  int getIterationNumber();
  
  long getIterationSeed();
  
  long getGlobalSeed();
  
  int getSizeHint();

  interface CounterExample<T> {
    /**
     * @return the value produced by the generator, on which the property check has failed
     */
    T getExampleValue();

    /**
     * @return the exception, if property check has failed with one
     */
    @Nullable
    Throwable getExceptionCause();

    /**
     * Re-run the generator and the property on the {@link DataStructure} corresponding to this counter-example.<p/>
     * 
     * This can be useful for debugging, when this example fails after some previous runs and shrinking, but doesn't fail
     * by itself. This can indicate unnoticed side effects in the generators and properties. Catching {@link PropertyFalsified}
     * exception, calling {@code replay} and putting some breakpoints might shed some light on the reasons involved.
     * @return a CounterExample with the results from the re-run. If re-run is successful (which also indicates some unaccounted side effects), a CounterExample is returned with an exception cause indicating that fact. 
     */
    @NotNull
    CounterExample<T> replay();
  }
  
}
