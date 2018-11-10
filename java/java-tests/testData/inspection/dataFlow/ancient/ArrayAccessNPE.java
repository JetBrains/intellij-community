class Test {
  public String foo(String[] path) {
    if (path != null) return null;
   
    String p = <warning descr="Array access 'path[0]' will produce 'NullPointerException'">path[0]</warning>;

    return "";
  }
}
