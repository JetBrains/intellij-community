
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class A {

  void m(final Stream<String> stream){
    Set<String> ALLOWED_PROPS = unmodifiableSet <error descr="'unmodifiableSet(java.util.Set<? extends java.lang.String>)' in 'A' cannot be applied to '(java.util.HashSet<java.lang.String>)'">(stream.collect(Collectors.toCollection(HashSet::new)))</error>;
  }

  public static <T1> Set<T1> unmodifiableSet(Set<? extends T1> s) {
    return null;
  }
}
