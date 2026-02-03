class QTest {
  final int myI = Source.CONST;
  public static void main(String[] args) {
    System.out.println(new QTest().my<caret>I);
  }
}

class Source {
  int CONST = 0;
}