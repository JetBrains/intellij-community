
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MethodReferenceTest {

  private final List<String> strings = Arrays.asList("One,Two", "Three,Four,Five");

  public static Stream<String> split(String csv) {
    return Arrays.asList(csv.split(",")).stream();
  }

  public void testMethodReference() {
    List<String> list = strings.stream()
      .flatMap(MethodReferenceTest::split)
      .collect(Collectors.toList());

  }

  public void testLambda() {
    List<String> list = strings.stream()
      .flatMap((t) -> MethodReferenceTest.split(t))
      .collect(Collectors.toList());
  }

  public void testMethodReferenceWithCast() {
    List<String> list = strings.stream()
      .flatMap((Function<String,Stream<String>>)MethodReferenceTest::split)
      .collect(Collectors.toList());

  }

  public void testAnonymousInnerClass() {
    List<String> strings = Arrays.asList("One,Two", "Three,Four,Five");

    List<String> list = strings.stream()
      .flatMap(new Function<String, Stream<String>>() {
        @Override
        public Stream<String> apply(String s) {
          return Arrays.asList(s.split(",")).stream();
        }
      })
      .collect(Collectors.toList());

  }
}