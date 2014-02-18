class B {}

class E extends B {}

class P<T> {}

class S<T> {}

class SS {

  static <T> S<T> stream(P<? extends T> spliterator) {
    return null;
  }

}

class Issue1 {

  public static void main(String[] args) {

    P<E> splb = null;

    S<B> sb2 = SS.stream(splb);

  }
}
