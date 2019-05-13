// "Remove redundant 'toString()' call" "false"
class Foo {
  public static void main(String[] args) {
    Object[] a = args;
    String s = a[0].toString<caret>(/*valuable comment!!!*/);
  }
}