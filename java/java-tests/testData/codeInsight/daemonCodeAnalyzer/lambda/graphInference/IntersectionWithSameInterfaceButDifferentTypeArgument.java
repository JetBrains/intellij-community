
import java.util.Collection;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MyTest {
  public <T> void from(Page<T> elements) { }

  public <T> void from(Collection<T> elements) { }

  public void foo(final Stream<String> artifactStream) {
    from<error descr="Ambiguous method call: both 'MyTest.from(Page<String>)' and 'MyTest.from(Collection<String>)' match">(artifactStream.collect(Collectors.toCollection(TreeSet<String>::new)))</error>;
  }

  interface Page<T> extends Iterable<T> {}
}
