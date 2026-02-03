// "Push down 'switch' expression" "true-preview"
class X {
  String print(int value) {
    return "[" + switch (value) {
        case 0 -> "zero";
        case 1 -> "one";
        default -> {
            if (value < 0) {
                yield "negative";
            }
            yield value;
        }
    } + "]";
  }
}