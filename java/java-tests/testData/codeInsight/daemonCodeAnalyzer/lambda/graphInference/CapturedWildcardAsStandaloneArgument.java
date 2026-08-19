import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// javac performs the method check on the upper bound of an actual argument type, so a captured wildcard
// of a standalone argument never leaks into the inferred type of the enclosing call
class CapturedWildcardAsStandaloneArgument {
  List<Function<List<String>, Object>> inLambdaBody(Stream<? extends Map.Entry<String, ?>> stream, List<String> l) {
    return stream
      .map(entry -> makeFunction(l, entry.getValue()))
      .collect(Collectors.toList());
  }

  List<List<Object>> nestedArgument(List<?> l) {
    return Collections.singletonList(Collections.singletonList(l.get(0)));
  }

  List<List<Object>> conditionalArgument(List<?> l, boolean b) {
    return Collections.singletonList(Collections.singletonList(b ? l.get(0) : l.get(0)));
  }

  List<List<Object>> parenthesizedArgument(List<?> l) {
    return Collections.singletonList(Collections.singletonList((l.get(0))));
  }

  // the result of a lambda body is not an argument, so the capture is preserved, as javac does
  List<List<Object>> lambdaBodyKeepsCapture(Stream<? extends List<?>> stream) {
    return stream.map(l -> l.subList(0, 1)).<error descr="Incompatible types. Found: 'java.util.List<java.util.List<capture<?>>>', required: 'java.util.List<java.util.List<java.lang.Object>>'">collect</error>(Collectors.toList());
  }

  // the same for the result of a method reference
  List<List<Object>> methodRefKeepsCapture(Stream<? extends Map.Entry<String, List<?>>> stream) {
    return stream.map(Map.Entry::getValue).<error descr="Incompatible types. Found: 'java.util.List<java.util.List<capture<?>>>', required: 'java.util.List<java.util.List<java.lang.Object>>'">collect</error>(Collectors.toList());
  }

  static <A, B> Function<A, B> makeFunction(A a, B b) {
    return null;
  }
}
