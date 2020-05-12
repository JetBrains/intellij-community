class MyTest {
  void v(Object... objects) { }
  void v(String... objects) { }

  void m(String[] values){
    v((Object[]) values);
    v((Object[]) null);

    v((<warning descr="Casting 'values' to 'String[]' is redundant">String[]</warning>) values);
    v((String[]) null);
  }
}