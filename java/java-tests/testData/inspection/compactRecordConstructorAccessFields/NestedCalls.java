class RecordMain {
  public record MyRecord(String name, int id) {

    public MyRecord {
      <warning descr="Calling method can access non-initialized fields">validateMyFields()</warning>;
      <warning descr="Calling method can access non-initialized fields">validateMyFieldsWithGetter()</warning>;
      nothing();
    }

    private void nothing() {

    }

    private static void t() {

    }
    private void validateMyFields() {
      if (this.name.isEmpty()) {
        throw new IllegalArgumentException();
      }
    }
    private void validateMyFieldsWithGetter() {
      if (this.name().isEmpty()) {
        throw new IllegalArgumentException();
      }
    }
  }


  public static void main(String[] args) {
    MyRecord myRecord = new MyRecord("s", 2);
    System.out.println(myRecord);
  }
}
