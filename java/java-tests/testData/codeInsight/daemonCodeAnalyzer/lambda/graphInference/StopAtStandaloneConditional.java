
class C {
  static <M extends Integer> M foo() {
    return null;
  }

  <G> G bar(G g, G gg) {
    return g;
  }

  void m(boolean b){
    bar(b ? foo() : null, foo());
  }
}
