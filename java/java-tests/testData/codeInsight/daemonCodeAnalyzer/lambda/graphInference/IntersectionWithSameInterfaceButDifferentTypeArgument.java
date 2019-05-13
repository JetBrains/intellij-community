
import java.util.Collection;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MyTest {
  public <T> void from(Page<T> elements) { }

  public <T> void from(Collection<T> elements) { }

  public void foo(final Stream<String> artifactStream) {
    from(artifactStream.collect(Collectors.toCollection(<error descr="Bad return type in method reference: cannot convert java.util.TreeSet<java.lang.String> to C">TreeSet<String>::new</error>)));
  }

  interface Page<T> extends Iterable<T> {}
}
