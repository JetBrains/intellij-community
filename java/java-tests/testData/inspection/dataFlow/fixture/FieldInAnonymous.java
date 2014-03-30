class Zoo2 {
  private boolean condition;
  private final boolean condition2;

  Zoo2(boolean condition2) {
    this.condition2 = condition2;
  }

  Runnable foo() {
    if (!condition) {
      return new Runnable() {

        public void run() {
          if (condition) {
            System.out.println("aaa");
          }
        }
      };
    }
    if (!condition2) {
      return new Runnable() {

        public void run() {
          if (<warning descr="Condition 'condition2' is always 'false'">condition2</warning>) {
            System.out.println("aaa");
          }
        }
      };
    }
    return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>;
  }

  public void setCondition(boolean condition) {
    this.condition = condition;
  }
}
