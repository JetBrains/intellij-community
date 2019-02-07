// "Replace 'switch' with 'if'" "true"
class Test {
  void foo(int x) {
    switch<caret> (x) {
      case 0,1 -> throw new IllegalArgumentException();
      case 2,3 -> {
        if (Math.random() > 0.5) break;
        System.out.println("two or three");
      }
      case 4 -> System.out.println("four");
      default -> {
        break;
      }
    }
  }
}