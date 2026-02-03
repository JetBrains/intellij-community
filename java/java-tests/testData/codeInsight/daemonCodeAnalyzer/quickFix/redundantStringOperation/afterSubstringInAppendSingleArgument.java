// "Remove redundant 'substring()' call" "true-preview"
class Foo {
  public void x(String arg) {
    StringBuilder sb = new StringBuilder();
    sb.append(arg, 2, arg.length());
  }
}