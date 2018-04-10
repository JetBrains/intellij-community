// "Remove redundant 'append()' call" "true"
class Foo {
  public static void main(String[] args) {
    StringBuilder sb = new StringBuilder();
    sb.append("foo").append("bar");
  }
}