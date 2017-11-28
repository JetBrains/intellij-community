// "Create field 'foo'" "true"
class Usage {

  void usage(Generic<String> g, List<String>[] p) {
    g.foo = p;
  }
}

class Generic<T> {

    public List<T>[] foo;
}
