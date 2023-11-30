// "Push down 'switch' expression" "false"
class X {
  void print(int value) {
    <caret>switch (value) {
      case 2 -> System.out.println("few");
      default -> throw new IllegalStateException();
    }
  }
}