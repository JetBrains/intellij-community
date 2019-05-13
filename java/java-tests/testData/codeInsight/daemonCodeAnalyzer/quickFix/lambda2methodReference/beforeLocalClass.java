// "Replace lambda with method reference" "true"
public class IDEA100452 {
  public static <T>  MatchOp<T> match(MatchOp.MatchKind matchKind) {
    class MatchSink extends BooleanTerminalSink<T> {

      private MatchSink() {
        super(matchKind);
      }

      @Override
      public void accept(T t) {
      }
    }

    Supplier<BooleanTerminalSink<T>> s = () -> new Match<caret>Sink();
    return new MatchOp<>(1, matchKind, s);
  }

  static abstract class BooleanTerminalSink<T> {
    public BooleanTerminalSink(MatchOp.MatchKind matchKind) {

    }

    public abstract void accept(T t);
  }
  static interface Supplier<T> {
    public T get();
  }

  static class MatchOp<H> {
    public MatchOp(int i, MatchKind matchKind, Supplier<BooleanTerminalSink<H>> s) {
      
    }

    static enum MatchKind {}
  }
}
