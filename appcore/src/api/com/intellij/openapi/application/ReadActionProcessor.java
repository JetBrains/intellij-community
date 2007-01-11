package com.intellij.openapi.application;

import com.intellij.util.Processor;
import com.intellij.openapi.util.Computable;

/**
 * @author cdr
 */
public abstract class ReadActionProcessor<T> implements Processor<T> {
  public boolean process(final T t) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>(){
      public Boolean compute() {
        return processInReadAction(t);
      }
    });
  }
  public abstract boolean processInReadAction(T t);
}
