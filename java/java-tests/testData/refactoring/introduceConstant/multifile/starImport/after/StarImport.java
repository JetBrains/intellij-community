import static p.StarImportApi.*;

class StarImport {
    public static final String xxx = bar(FOO + "3");

    void foo() {
    String x = xxx;
  }

  static String bar(String x) { return x; }
}