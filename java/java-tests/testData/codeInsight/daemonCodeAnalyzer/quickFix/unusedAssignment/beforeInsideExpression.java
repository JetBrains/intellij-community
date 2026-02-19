// "Remove redundant assignment" "true-preview"
class A {
  public static void main(String[] args) {
    int x = (<caret>x = 3) * 4;
    System.out.println(x);
  }
}