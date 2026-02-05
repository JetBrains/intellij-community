import static p.StarImportApi.*;

class StarImport {
  void foo() {
    String x = b<caret>ar(FOO + "3");
  }

  static String bar(String x) { return x; }
}