
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

class Test {
  public <T> void some() {
    Function<AtomicReference<T>, ? extends Class<? extends AtomicReference>> a = AtomicReference<T>::getClass;
    Function<AtomicReference<T>, ? extends Class<? extends AtomicReference>> b = AtomicReference::getClass;
  }
}

class Test1<M> {

  public <T> void some(Stream<Test1<T>> stream) {
    stream.map(Test1::getClass);
    stream.map(Test1<T>::getClass);
  }
  public String getClass(String name) {
    return null;
  }
}

class Test2<M> {

  public <T> void some(Stream<Test2<T>> stream) {
    stream.map(Test2::getClass);
    stream.map(Test2<T>::getClass);
  }
}