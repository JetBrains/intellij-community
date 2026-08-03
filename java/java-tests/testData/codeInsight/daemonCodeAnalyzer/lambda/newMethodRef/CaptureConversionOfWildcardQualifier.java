import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

class GenClass<T extends Number> {
  T field;

  T field() {
    return field;
  }

  void setField(T t) { }

  List<T> list() {
    return null;
  }
}

class Assert<T> {
  <U> Assert<U> extracting(Function<? super T, U> extractor) {
    return new Assert<>();
  }

  void matches(Predicate<? super T> predicate) { }
}

class Test {
  void reported(Assert<GenClass<Number>> assertObj) {
    assertObj.extracting(GenClass<?>::field).matches(x -> x.intValue() == 42);
  }

  void unbound() {
    Function<GenClass<?>, Number> f1 = GenClass<?>::field;
    //capture conversion is applied to the qualifier, not to the substituted return type
    Function<GenClass<?>, List<? extends Number>> f2 = GenClass<?>::list;
    Function<GenClass<? extends Integer>, Integer> f3 = GenClass<? extends Integer>::field;
    Function<GenClass<? super Integer>, Number> f4 = GenClass<? super Integer>::field;
  }

  void bound(GenClass<?> g) {
    Supplier<Number> s1 = g::field;
    Supplier<List<? extends Number>> s2 = g::list;
  }

  void capturedParameterIsNotAssignable() {
    //Number is not assignable to the captured wildcard
    BiConsumer<GenClass<Number>, Number> bc = GenClass<?>::<error descr="Incompatible types: Number is not convertible to capture of ?">setField</error>;
  }
}

record GenRecord<T extends Number>(T field) { }

class RecordTest {
  //the same for the synthesized record accessor
  void reported(Assert<GenRecord<Number>> assertObj) {
    assertObj.extracting(GenRecord<?>::field).matches(x -> x.intValue() == 42);
  }

  void unbound() {
    Function<GenRecord<?>, Number> f = GenRecord<?>::field;
  }
}
