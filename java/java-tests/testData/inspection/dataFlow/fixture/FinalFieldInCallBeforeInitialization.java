class FailingNonNull {
  private final String nullable;

  public FailingNonNull() {
    Inner inner = new Inner();
    inner.doNullableStuff();
    nullable = "now non-null";
  }

  private class Inner{
    public void doNullableStuff() {
      if (nullable !=null) { //Condition 'nullable!=null' is always 'true'
        System.out.println(nullable.length());
      }
    }
  }
  public static void main(String[] args) {
    new FailingNonNull();
  }
}