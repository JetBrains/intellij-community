class C {
  void foo(String arg) throws Exception {
    Object[] objects;
    throw new Exception("");
    <error descr="Unreachable statement">objects[0] = arg;</error>
  }
}