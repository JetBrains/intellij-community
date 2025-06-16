// "Convert to record class" "true-preview"
class <caret>R {
  final int first;
  final String second;
  final int[] third;
  final boolean fourth;

  private R(int first, String second, int... third, boolean fourth) {
    this.first = first;
    this.second = second;
    this.third = third;
    this.fourth = fourth;
  }

  private int getFirst() {
    return first > 0 ? first : -first;
  }

  String getSecond() {
    return second.length() > 1 ? second : "";
  }

  private int[] getThird() {
    return third;
  }

  boolean isFourth() {
    return fourth;
  }
}
