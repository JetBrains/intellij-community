import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;

class Main {
  public static class Either<A, B> {
    final public Optional<A> _1;
    final public Optional<B> _2;

    private Either(Optional<A> _1, Optional<B> _2) {
      this._1 = _1;
      this._2 = _2;
    }

    public static <A, B> Either<A, B> _2(B value) {
      return null;
    }
  }

  public Either<String, Exception> test1() {
    try {
      new FileOutputStream("").write(1);
      return null;
    } catch (NullPointerException | IOException e) {
      return Either._2(e);
    }
  }
}