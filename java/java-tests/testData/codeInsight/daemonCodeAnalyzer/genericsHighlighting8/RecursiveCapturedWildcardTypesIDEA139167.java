
import java.util.List;

interface A<T extends List<String>> {
  T m();
}

abstract class C {
  abstract <S> A<? extends S> foo();
  void bar() {
    this.<List<?>>foo().m().get(0).toLowerCase();
  }
}