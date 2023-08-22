package com.siyeh.igtest.abstraction.covariance;

import my.*;
import java.util.function.*;
import java.util.*;

public class InvariantSwitchedOff<T> {
  // no wildcards for invarant class
  <T> boolean containsNull(List<T> list) {
    for (T t : (list)) {
      if (t == null) return true;
    }
    return (list).get(0) == null;
  }

  public void foooo(Processor<<warning descr="Can generalize to '? super Number'">Number</warning>> pass) {
    pass.process(1);
  }

  ////////////// field assigned from method
  class S {
    Processor<S> myProcessor;

    public S(Processor<<warning descr="Can generalize to '? super S'">S</warning>> myProcessor) {
      this.myProcessor = myProcessor;
    }

    boolean foo(S s) {
      return myProcessor.process(s);
    }
  }

  Number foo(Supplier<<warning descr="Can generalize to '? extends Number'">Number</warning>> f) {
    return f.get();
  }
}