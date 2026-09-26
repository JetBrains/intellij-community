import java.util.*;


class MyTest {
   <T> java.util.List<T> f(F.B ff) {
     return null;
   }

    {
        this.<String>f<caret>(new F<>.B());
    }
}

class F<T> {
  class B {}
}
