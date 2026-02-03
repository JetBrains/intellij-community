class TestClass {
    public final class TestMapper<X>  {

        public <U, T extends Mapping<X, U>> T mapType() {
            SimpleMapping<X, U> mapping = new SimpleMapping<X, U>();
            return (T) mapping;   //This is reports "Inconvertible types; cannot cast TestClass.SimpleMapping<X,U> to 'T'"
        }
    }
    private final class SimpleMapping<X, U> implements Mapping<X, U> {}
    public interface Mapping<F, U> {}
}
class TestClass1 {
    public final class TestMapper<X>  {

        public <U, T extends Mapping<X, U>> T mapType() {
            Mapping<X, U> mapping = new SimpleMapping<X, U>(); //Changed type to interface
            return (T) mapping;
        }
    }
    private final class SimpleMapping<X, U> implements Mapping<X, U> {}
    public interface Mapping<F, U> {}
}
