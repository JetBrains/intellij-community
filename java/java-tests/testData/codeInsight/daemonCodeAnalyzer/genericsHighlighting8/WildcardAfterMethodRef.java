import java.util.function.Supplier;

interface Main {
  interface X<T> {
    X<T> self();
  }

  static X<?> makeX() {return null;}

  static <R> X<R> create(Supplier<? extends R> supplier) {return null;}

  static X<X<?>> methodRef() {
    // Javac compiles this, ECJ does not. See IDEA-378878.
    return create(Main::makeX).<error descr="Incompatible types. Found: 'Main.X<? extends Main.X<?>>', required: 'Main.X<Main.X<?>>'">self</error>();
  }

  static X<X<?>> lambda() {
    return create(() -> makeX()).<error descr="Incompatible types. Found: 'Main.X<? extends Main.X<?>>', required: 'Main.X<Main.X<?>>'">self</error>();
  }
}
