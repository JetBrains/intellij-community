
class C<M extends L, L> {}

class B<K extends C<?, ?>>{
  K get() {
    return null;
  }

  {
    C c = get();
  }
}