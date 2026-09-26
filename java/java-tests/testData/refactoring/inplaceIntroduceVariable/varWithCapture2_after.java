public class VarG {
  public static void main(String[] args) {
    Foo<? extends Bar<? extends Bar<? extends String>>> a = new Foo<>();
      var s = a.s();
  }
}

class Foo<T> {
  T s() {
    return null;
  }
}

class Bar<T> {
}