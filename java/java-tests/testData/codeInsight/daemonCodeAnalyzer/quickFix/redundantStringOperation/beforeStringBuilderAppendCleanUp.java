// "Fix all 'Redundant 'String' operation' problems in file" "true"
class Foo {
  public static void main(String[] args) {
    StringBuilder sb = new StringBuilder();
    sb.appe<caret>nd("");
    (sb = new StringBuilder()).append("");
    (sb.append("x")).append("");
  }
}