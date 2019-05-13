class Main<T> {

  {
    I<Main<String> > aNew = Main[]::new;
    I<Main<?> > aNew1 = Main<?>[]::new;
    I<Main<? extends String>> aNew2 = <error descr="Generic array creation">Main<? extends String>[]</error>::new;

    I<int[]> p = int[][]::new;
    I<Main<String>[]> a = Main[][]::new;
    I<Main<?>[]> a1 = Main<?>[][]::new;
    I<Main<? extends String>[]> a2 = <error descr="Generic array creation">Main<? extends String>[][]</error>::new;

    I<Inner<String>> inn1 = Main.Inner[]::new;
    I<Main<?>.Inner<?>> inn2 = Main<?>.Inner<?>[]::new;
    I<Main<String>.Inner<String>> inn3 = <error descr="Generic array creation">Main<String>.Inner<String>[]</error>::new;
    I<Main<?>.Inner<?>> inn4 = Main<?>.Inner<?>[]::<String>new;
  }

  class Inner<P> {}

  interface I<K> {
    K[] v(int i);
  }
}
