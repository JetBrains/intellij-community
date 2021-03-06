// "Convert to a record" "true"
final class <caret>R {
    final int first;
    final String second;
    final int[] third;

    private R(int first, String second, int... third) {
      this.first = first;
      this.second = second;
      this.third = third;
    }

    private int first() {
      return first > 0 ? first : -first;
    }

    String getSecond() {
      return second.length() > 1 ? second : "";
    }

    private int[] getThird() {
      return third;
    }
}