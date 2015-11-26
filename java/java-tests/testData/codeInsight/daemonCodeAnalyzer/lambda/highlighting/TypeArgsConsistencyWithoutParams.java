import java.lang.String;
import java.util.*;
class Test4 {
   interface I<K> {
       List<K> foo();
   }
    
    static <T> void bar(I<T> i){}
    
    {
        bar(() -> null);
    }
}

class Test5 {
    interface I<K> {
        void foo(K k);
    }

    static <T> void bar(I<T> i){}

    {
        bar(<error descr="Incompatible parameter types in lambda expression">() -> null</error>);
    }
}
class Test6 {
    interface I<K> {
        void foo();
    }

    static <T> void bar(I<T> i){}

    {
        bar(<error descr="Incompatible types: expected void but the lambda body is neither a statement expression nor a void-compatible block">() -> null</error>);
        bar(() -> {});
    }
}

class Test7 {
  interface I<K> {
    void foo(String s);
  }
  
  static <T> void bar(I<T> i){}

  {
     bar(x -> {});
  }
}