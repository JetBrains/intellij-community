interface Comparable<X> {
    boolean compare (X a, X b);
}

class Test {
  <T> void sort(T[] x, Comparable<T> c){
  }

  void f(){
    sort(
      new String[1],
      new Comparable(){
        public boolean compare(Object a, Object b) {
          return true;
        }
      }
    );
  }
}
