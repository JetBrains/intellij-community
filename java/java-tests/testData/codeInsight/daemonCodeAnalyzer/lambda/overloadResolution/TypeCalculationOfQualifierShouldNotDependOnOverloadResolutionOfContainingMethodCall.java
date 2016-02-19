
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Main {

  private void <warning descr="Private method 'createFunction(java.util.stream.Stream<java.util.Set<java.lang.String>>)' is never used">createFunction</warning>(Stream<Set<String>> setStream) {
    foo (setStream.flatMap(Collection::stream).collect(Collectors.toList()));
  }

  private void foo(Collection<String> keys) {
    System.out.println(keys);
  }
  private void <warning descr="Private method 'foo(java.util.Set<java.lang.String>)' is never used">foo</warning>(Set<String> keys) {
    System.out.println(keys);
  }

}
