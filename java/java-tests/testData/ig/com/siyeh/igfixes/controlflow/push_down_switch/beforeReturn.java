// "Push down 'switch' expression" "true-preview"
class X {
  String print(int value) {
    return <caret>switch (value) {
      case 0 -> "[" + "zero" + "]";
      case 1 -> "[" + "one" + "]";
      default -> "[" + value + "]";
    };
  }
}