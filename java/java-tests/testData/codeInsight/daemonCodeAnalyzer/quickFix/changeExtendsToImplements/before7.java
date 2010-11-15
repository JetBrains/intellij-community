// "Change 'implements b' to 'extends b'" "true"
class a implements <caret>b<C.D> {
}

class b<T> {}

class C {
  static class D {}
}
