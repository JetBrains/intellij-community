package com.siyeh.igtest.errorhandling.new_exception_without_arguments;

import java.util.Optional;
import java.util.function.Function;

class NewExceptionWithoutArguments {

  void foo() {
    throw new <warning descr="'new RuntimeException()' without arguments">RuntimeException</warning>();
  }

  void bar() {
    throw new MyException();
  }

  void x() {
    Optional.ofNullable(null).orElseThrow(<warning descr="'new RuntimeException()' without arguments">RuntimeException</warning>::new);
    Optional.ofNullable(null).orElseThrow(() -> new <warning descr="'new RuntimeException()' without arguments">RuntimeException</warning>());
    Function<String, Exception> f = Exception::new;
  }

}
class MyException extends RuntimeException {

}