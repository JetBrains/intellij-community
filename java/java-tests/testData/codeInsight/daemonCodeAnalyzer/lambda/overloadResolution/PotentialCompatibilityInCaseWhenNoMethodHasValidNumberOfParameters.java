
class Test {
  public <T> void varargs(int i, String... p1) { }
  public <T> void usage(String p1) { }
  {
    varargs<error descr="Cannot resolve method 'varargs()'">()</error>;
    varargs(1);
    varargs(1, "");
    varargs(1, "", "");
    usage<error descr="Expected 1 argument but found 0">()</error>; 
    usage("");
    usage<error descr="Expected 1 argument but found 2">("", "")</error>;
  }
}