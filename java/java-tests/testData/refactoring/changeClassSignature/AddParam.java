class L<E> {}
class <caret>C<T> {
}

class Usage extends C<String> {
  {
    C<? extends Integer> c = new C<Integer>();
  }
}