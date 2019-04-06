import java.lang.Double;

interface IConverter<C> {
}

abstract class AbstractNumberConverter<N extends Number> implements IConverter<N> {
}

class DoubleConverter extends AbstractNumberConverter<Double> {
}

class Test {
  public static <C> IConverter<C> getConverter(Class<C> type) {
    return (IConverter<C>)new DoubleConverter() {
    };
  }

  public static <C extends String> IConverter<C> getConverter1(Class<C> type) {
    return <error descr="Inconvertible types; cannot cast 'anonymous DoubleConverter' to 'IConverter<C>'">(IConverter<C>)new DoubleConverter() {
    }</error>;
  }

  public static <C extends Double> IConverter<C> getConverter2(Class<C> type) {
    return (IConverter<C>)new DoubleConverter() {
    };
  }

  public static void main(String[] args) {
    IConverter<String> converter = getConverter(String.class);
    IConverter<String> converter1 = getConverter1(String.class);
    IConverter<String> converter2 = <error descr="Inferred type 'java.lang.String' for type parameter 'C' is not within its bound; should extend 'java.lang.Double'">getConverter2(String.class)</error>;
  }
}


class Z {

}

class TestNonNarrowingConversion<T extends Z> {
    public TestNonNarrowingConversion(T u) {

    }

    public T z = null;

    public int a() {
        <error descr="Incompatible types. Found: 'TestNonNarrowingConversion<Z>', required: 'TestNonNarrowingConversion<T>'">TestNonNarrowingConversion<T> x = new TestNonNarrowingConversion<Z>(new Z());</error>
        return 1;
    }
}
class TestRecursiveTypeParameter {
  static <<error descr="Cyclic inheritance involving 'T'"></error>T extends T> void test(T t) {
    <error descr="Incompatible types. Found: 'T', required: 'java.lang.String'">String x = t;</error>
    <error descr="Incompatible types. Found: 'java.lang.String', required: 'T'">t = x</error>;
  }

  static <<error descr="Cyclic inheritance involving 'A'"></error>A extends B, B extends A> void test(A a, B b) {
    a = b;
    b = a;
    <error descr="Incompatible types. Found: 'A', required: 'java.lang.String'">String x = a;</error>
    <error descr="Incompatible types. Found: 'java.lang.String', required: 'A'">a = x</error>;
  }
}