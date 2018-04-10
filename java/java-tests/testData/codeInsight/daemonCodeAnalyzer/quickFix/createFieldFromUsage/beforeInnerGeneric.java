// "Create field 'foo'" "true"
class Usage {

  void usage(Generic<String> g, List<String> p) {
    g.f<caret>oo = p;
  }
}

class Generic<T> {

}
