// "Push down 'switch' expression" "true-preview"
class X {
  void print(int value) {
    <caret>switch (value) {
      case 2 -> System.out.println("few");
      case 3 -> System.out.println("few");
      case -1 -> System.out.println("zero");
      case 0 -> System.out.println("zero");
      case 1 -> System.out.println("one");
      case 4 -> System.out.println("few");
      default -> throw new IllegalStateException();
    }
  }
}