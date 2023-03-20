// "Remove redundant 'toString()' call" "true-preview"
class Foo {
  public static void main(String[] args) {
    String s = args[0].toString<caret>(/*valuable comment!!!*/);
  }
}