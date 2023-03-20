import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;

public class TestBlockingConstructor {
  static class BlockingConstructor {
    @Blocking
    BlockingConstructor() {
    }

    @Blocking
    BlockingConstructor(String url) {
    }
  }

  static class Intermediate extends BlockingConstructor {}

  static class NonBlockingConstructor extends BlockingConstructor {
    @NonBlocking
    <warning descr="Possibly blocking call from implicit constructor call in non-blocking context could lead to thread starvation">NonBlockingConstructor</warning>() {

    }

    @NonBlocking
    NonBlockingConstructor(String url) {
      <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">super</warning>(url);
    }
  }

  static class NonBlockingConstructorIntermediate extends Intermediate {
    @NonBlocking
    <warning descr="Possibly blocking call from implicit constructor call in non-blocking context could lead to thread starvation">NonBlockingConstructorIntermediate</warning>() {

    }
  }
}