interface Comparable<T extends Comparable<T>> {}
class List<T> {}
class Collections {
  static <T extends Comparable<? super T>> void sort(List<T> list) {}
  static <T> void sort(List<T> list, Comparator<? super T> c) {}
}

class Foo implements Comparable<Foo> {
  public static void main(String[] args){
    List<Foo> list;
    Collections.sort(l<caret>)
  }
}