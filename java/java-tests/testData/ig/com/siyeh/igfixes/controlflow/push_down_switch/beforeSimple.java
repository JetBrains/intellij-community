// "Push down 'switch' expression" "true-preview"
class X {
  void print(int value) {
    switch<caret> (value) {
      case 0 -> System.out.println("zero");
      case 1 -> System.out.println("one");
      case 2, 3, 4 -> System.out.println("few");
      default -> System.out.println("many");
    }
  }
}