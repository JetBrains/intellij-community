
class Test {
  public <T> void varargs(int i, String... p1) { }
  public <T> void usage(String p1) { }
  {
    varargs<error descr="Cannot resolve method 'varargs()'">()</error>;
    varargs(1);
    varargs(1, "");
    varargs(1, "", "");
    usage<error descr="'usage(java.lang.String)' in 'Test' cannot be applied to '()'">()</error>; 
    usage("");
    usage<error descr="'usage(java.lang.String)' in 'Test' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
  }
}