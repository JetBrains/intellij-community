class QTest {
  final int myI = foo();
  int foo(){return 0;}
  public static void main(String[] args) {
    System.out.println(new QTest().my<caret>I);
  }
}