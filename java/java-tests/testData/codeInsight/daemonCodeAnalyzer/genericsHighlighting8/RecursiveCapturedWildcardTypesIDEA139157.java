
import java.util.*;

interface A<T extends A<? extends A<T>>> { }

class C {
  void foo(List<A<? extends A<?>>> x){
    List<A<?>> y = x;
  }
}