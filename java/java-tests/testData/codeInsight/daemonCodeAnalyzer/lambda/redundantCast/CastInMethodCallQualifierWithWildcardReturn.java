
interface Pair<A extends String> {
  A get();
}

class B {
  void m(final Pair<?> p) {
    String v = ((<warning descr="Casting 'p' to 'Pair<?>' is redundant">Pair<?></warning>) p).get();
  }
}

