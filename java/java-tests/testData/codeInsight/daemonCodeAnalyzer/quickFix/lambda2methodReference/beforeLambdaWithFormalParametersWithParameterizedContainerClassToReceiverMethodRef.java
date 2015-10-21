// "Replace lambda with method reference" "true"

import java.util.function.Function;

class Test<T>  {


  class Bar {
    void f( ){
      Function<Test<T>.Bar, String> r = (Test<T>.Bar t) -> t.fo<caret>o();
    }

    private String foo() {}
  }
}
