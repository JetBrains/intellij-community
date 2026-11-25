import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

class DiamondAnon {
  static class Base<I, O> {
    Base(List<? extends I> pIn, Function<I, O> pTransform) {}
    protected void listen(Consumer<I> pListener) {}
  }

  static class Col<S, T> {
    Function<Feat<S, T>, Val<T>> factory() { return null; }
  }

  static class Feat<S, T> {
    Feat(Col<S, T> pCol) {}
  }

  interface Val<T> {}

  static void rawCallInLambda(List<Col<String, ?>> pCols) {
    final Base<Col<String, ?>, Val<?>> base = new Base<>(
      pCols, pCol -> pCol.factory().apply(new Feat(pCol))
    ) {
      @Override protected void listen(Consumer<Col<String, ?>> pListener) {}
    };
  }

  static void noRawCall(List<Col<String, ?>> pCols) {
    final Base<Col<String, ?>, Val<?>> base = new Base<>(pCols, pCol -> null) {
      @Override protected void listen(Consumer<Col<String, ?>> pListener) {}
    };
  }
}
