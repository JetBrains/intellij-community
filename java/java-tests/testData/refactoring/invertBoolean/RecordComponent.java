record MyRecord(boolean <caret>isArray) {

  public MyRecord() {
    this(true);
  }

  public MyRecord(boolean isArray, String s) {
    this(isArray);
  }

  @Override
  public boolean isArray() {
    System.out.println(isArray);
    return (isArray);
  }

  public static void main(String[] args) {
    new MyRecord(false, "<UNK>");
  }
}
class Main {
  public static void main(String[] args) {
    final MyRecord record = new MyRecord(true);
    if (record.isArray()) {
      System.out.println("array");
    }
  }
}