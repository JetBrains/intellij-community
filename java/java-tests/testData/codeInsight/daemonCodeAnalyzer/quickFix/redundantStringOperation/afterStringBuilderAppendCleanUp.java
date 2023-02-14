// "Fix all 'Redundant 'String' operation' problems in file" "true"
class Foo {
  public static void main(String[] args) {
    StringBuilder sb = new StringBuilder();
      sb = new StringBuilder();
      sb.append("x");
  }
}