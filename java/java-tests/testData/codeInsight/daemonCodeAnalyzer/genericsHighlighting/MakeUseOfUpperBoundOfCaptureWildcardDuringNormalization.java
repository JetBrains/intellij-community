import java.util.List;

class G<T extends List<String> & Runnable> {
  T get() {return null;}
}

interface I extends List<String>, Runnable {}

abstract class Test {
  abstract G<? super I> m();

  {
    m().get().run();
    String s = m().get().get(0);
  }
}