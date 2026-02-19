// "Use 'contentEquals()' and remove redundant 'toString()' call" "true-preview"
class Main {
  boolean foo(String s, StringBuilder sb) {
    return s.contentEquals(sb);
  }
}