// "Replace lambda with method reference" "true-preview"

import java.util.function.Function;

class Test<T>  {


  class Bar {
    void f( ){
      Function<Test<T>.Bar, String> r = Bar::foo;
    }

    private String foo() {}
  }
}
