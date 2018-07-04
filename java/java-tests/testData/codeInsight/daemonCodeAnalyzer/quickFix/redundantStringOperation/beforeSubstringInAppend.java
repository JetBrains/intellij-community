// "Remove redundant 'substring()' call" "true"
class Foo {
  public static void main(String[] args) {
    StringBuilder sb = new StringBuilder();
    sb.append(args[0].subs<caret>tring(3, 4));
  }
}