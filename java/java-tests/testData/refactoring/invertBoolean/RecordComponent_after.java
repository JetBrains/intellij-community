record MyRecord(boolean isArrayInverted) {

  public MyRecord() {
    this(false);
  }

  public MyRecord(boolean isArray, String s) {
    this(isArray);
  }

  public boolean isArrayInverted() {
    System.out.println(!isArrayInverted);
    return (isArrayInverted);
  }

  public static void main(String[] args) {
    new MyRecord(true, "<UNK>");
  }
}
class Main {
  public static void main(String[] args) {
    final MyRecord record = new MyRecord(false);
    if (!record.isArrayInverted()) {
      System.out.println("array");
    }
  }
}