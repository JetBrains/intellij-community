import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.*;

abstract class MapToGeneric {
  void sdf(Stream<MapToGeneric> stream) {
    stream.map(mapToGeneric -> mapToGeneric.map("123"));
  }

  abstract <T> Map<?, T> map(T t);
}


abstract class MapToGenericSimplified {
  {
    bar(() -> map()) ;
    bar(this::map) ;
  }

  abstract <T> Map<T, ?> map();

  abstract <R> R bar(Supplier<R> mapper);
}

class Test {
  static class MyList<T, X> extends ArrayList<X> {}

  static <T> MyList<? extends Number, T> create() {
    return new MyList<>();
  }

  MyList<? extends Number, ? extends Number> test(List<? extends Number> list) {

    return list.stream().collect(Collectors.toCollection(Test::create));
  }

  MyList<? extends Number, ? extends Number> testLambda(List<? extends Number> list) {

    return list.stream().collect(Collectors.toCollection(() -> create()));
  }
}


class TestWithNonProperTypeBeforeCapture {
  {
    Set<? super List<String>> set = foo();
  }

  private <K> Set<? super List<K>> foo() { //#CAP<? super List<K>> is not proper, can't be used as eq bound
    return null;
  }
}

class TestWithNonPropertTypeBeforeCaptureInMethodCall {
  private <K, V> void test(Stream<Map.Entry<K, V>> stream, BiFunction<K, V, ?> entryMapper) {
    stream.map(createToStringMapper(entryMapper));
  }

  private <K, V> Function<? super Map.Entry<K, V>, String> createToStringMapper(BiFunction<K, V, ?> mapper) {
    return k -> toString();
  }
}