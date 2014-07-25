class Main {
  public static class InnerClass {
    private String field;
  }
  public static class InnerSubClass extends InnerClass {
    public String getParentField() {
      return this.<error descr="'field' has private access in 'Main.InnerClass'">field</error>;
    }
  }
}
