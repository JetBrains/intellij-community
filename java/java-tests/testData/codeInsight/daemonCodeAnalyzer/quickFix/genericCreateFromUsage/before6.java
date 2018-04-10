// "Create method 'foo'" "true"
interface Generic<T> {
}

class Usage {

  List<List<String>> usage(Generic<String> g) {
    return g.fo<caret>o();
  }
}
