import<caret> java.util.List;

class HighlightImport<T extends List> {

  void f(List l) {
    List m;
    java.util.List n;
  }

  List<List> g() {
    return null;
  }
}