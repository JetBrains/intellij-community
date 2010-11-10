// "Change 'implements b' to 'extends b'" "true"
class a extends b<C.D> {
}

class b<T> {}

class C {
  static class D {}
}
