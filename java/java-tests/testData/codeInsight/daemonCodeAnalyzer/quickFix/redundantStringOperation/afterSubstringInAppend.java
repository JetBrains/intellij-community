// "Remove redundant 'substring()' call" "true-preview"
class Foo {
  public static void main(String[] args) {
    StringBuilder sb = new StringBuilder();
    sb.append(args[0], 2, 4);
  }
}