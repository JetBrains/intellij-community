// "Simplify" "true"
class Test {
  public static void main(String[] args) {
    int i = 0;
    System.out.println(false <caret>? i : 'x');
  }
}