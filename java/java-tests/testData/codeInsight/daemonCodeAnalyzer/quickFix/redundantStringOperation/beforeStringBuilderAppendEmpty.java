// "Remove redundant 'append()' call" "true-preview"
class Foo {
  public static void main(String[] args) {
    StringBuilder sb = new StringBuilder();
    sb.append("foo").ap<caret>pend(("")).append("bar");
  }
}