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
    IConverter<String> converter2 = getConverter2<error descr="'getConverter2(java.lang.Class<C>)' in 'Test' cannot be applied to '(java.lang.Class<java.lang.String>)'">(String.class)</error>;
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
