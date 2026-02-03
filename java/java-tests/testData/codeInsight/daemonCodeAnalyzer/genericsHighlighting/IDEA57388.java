
class B<T extends Cloneable> {}
class A<T> {
  A<B<? extends Cloneable>> foo(A<B<?>> x){
    return x;
  }
}
