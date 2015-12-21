import java.util.List;

class SimpleDependency  {

  interface I<R extends U, U> {
    R m();
  }

  {
    I<? extends String, ? extends  String> k = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends String, String> k1 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends List<String>, List<String>> k2 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends List<String>, ? extends List<String>> k3 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends List<? extends String>, ? extends List<String>> k4 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends List<? extends String>, List<? extends String>> k5 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends List<? extends String>, ? extends List<? extends String>> k6 = <error descr="Cannot infer functional interface type">() -> null</error>;

    I<? super String, String> s = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? super List<String>, List<? extends String>> s1 = <error descr="Cannot infer functional interface type">() -> null</error>;
  }
}

class NoDependency {
  interface I<T, U> {
    T m();
  }

  {
    I<? extends String, ? extends String> k = () -> null;
  }
}

class ExtendsList {
  interface I<R extends List<T>, T> {
    R m();
  }

  {
    I<?, ? extends String> n = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<?, ?> n1 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<?, String> n2 = <error descr="Cannot infer functional interface type">() -> null</error>;


    I<? extends List<?>, String> e1 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends List<?>, ?> e2 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends List<String>, ? extends String> e3 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends List<? extends String>, ? extends String> e4 = <error descr="Cannot infer functional interface type">() -> null</error>;

    I<? super List<String>, ? extends String> s1 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? super List<String>, String> s2 = <error descr="Cannot infer functional interface type">() -> null</error>;
  }
}

class MultipleBounds {
  interface I<R extends List<T> & Comparable<T>, T> {
    R m();
  }

  interface LC<K> extends List<K>, Comparable<K> {}

  {
    I<?, String> n = <error descr="Cannot infer functional interface type">() -> null</error>;

    I<? extends List<String>, ? extends String> e1 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends Comparable<String>, ? extends String> e2 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends LC<String>, ? extends String> e3 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends LC<String>, String> e4 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends LC<? extends String>, String> e5 = <error descr="Cannot infer functional interface type">() -> null</error>;
  }
}

class FirstIndependentBound {
  interface I<R extends List<String> & Comparable<T>, T> {
    R m();
  }

  interface LC<K> extends List<String>, Comparable<K> {}

  {
    I<?, String> n = <error descr="Cannot infer functional interface type">() -> null</error>;

    I<? extends List<String>, ? extends String> e1 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends Comparable<String>, ? extends String> e2 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends LC<String>, ? extends String> e3 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends LC<String>, String> e4 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends LC<? extends String>, String> e5 = <error descr="Cannot infer functional interface type">() -> null</error>;
  }
}


class SecondIndependentBound {
  interface I<R extends List<T> & Comparable<String>, T> {
    R m();
  }

  interface LC<K> extends List<String>, Comparable<K> {}

  {
    I<?, String> n = <error descr="Cannot infer functional interface type">() -> null</error>;

    I<? extends List<String>, ? extends String> e1 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends Comparable<String>, ? extends String> e2 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends LC<String>, ? extends String> e3 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends LC<String>, String> e4 = <error descr="Cannot infer functional interface type">() -> null</error>;
    I<? extends LC<? extends String>, String> e5 = <error descr="Cannot infer functional interface type">()  -> null</error>;
    I<? extends LC<? extends String>, ? extends String> e6 = <error descr="Cannot infer functional interface type">()  -> null</error>;
  }
}