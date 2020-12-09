// "Convert to a record" "false"
class <caret>R {
    private final int first;
    private final int second;

    <T> R(int first, int second) {
      this.first = first;
      this.second = second;
    }
}