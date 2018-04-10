// "Create method 'foo'" "true"
interface Generic<T> {
    List<List<T>> foo();
}

class Usage {

  List<List<String>> usage(Generic<String> g) {
    return g.foo();
  }
}
