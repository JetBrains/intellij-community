// "Convert to record class" "true-preview"

value class UserId<caret> {
  private final int value;

  public UserId(int value) {
    this.value = value;
  }

  public int value() {
    return value;
  }
}
