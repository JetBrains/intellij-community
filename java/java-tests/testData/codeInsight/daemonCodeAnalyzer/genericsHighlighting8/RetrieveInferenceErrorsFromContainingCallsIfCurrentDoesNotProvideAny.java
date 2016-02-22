import java.util.List;
import java.util.function.Function;

class Test {
  boolean foo(String string) {
    return true;
  }
  
  <K> List<K> bar(Function<K, Boolean> f) {
    return null;
  }

  {
    List<Integer> l = bar(k -> foo<error descr="'foo(java.lang.String)' in 'Test' cannot be applied to '(java.lang.Integer)'">(k)</error>);
  }
}

