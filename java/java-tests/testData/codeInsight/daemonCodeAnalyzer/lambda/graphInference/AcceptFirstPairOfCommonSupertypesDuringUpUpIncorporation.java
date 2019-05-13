interface I<T>{}
abstract class A<T> implements I<A<T>>{}
class Factory {
  static <T extends A<?>> T get(Class<T> c){
    return null;
  }
}

class Impl extends A<Impl> {
  static Impl get() {
    return Factory.get(Impl.class);
  }
}