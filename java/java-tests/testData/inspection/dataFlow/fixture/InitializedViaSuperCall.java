class Example {
  public static class Extended extends Base {

    private String field;

    public Extended() {
      super();
      // Do not expect warning on field.trim(), as it's initialized via superclass constructor
      System.out.println(field.trim());
    }

    @Override
    protected void init() {
      field = "Set";
    }
  }

  public static class Base {

    public Base() {
      init();
    }

    protected void init() {

    }

  }

  public static void main(String[] args) {
    new Extended();
  }

}
