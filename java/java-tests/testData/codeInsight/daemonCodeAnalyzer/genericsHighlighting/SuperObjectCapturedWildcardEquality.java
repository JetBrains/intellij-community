class A<T> {}

class Test {
  {
    A<? super Object> queue = null;
    A<Object> q = queue;
    queue =  q;
  }
}