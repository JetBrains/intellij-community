class Example {

  private int i = 1;
  private int <warning descr="Field can be converted to a local variable">j</warning> = 2;
  private int k = 3;
  private int <warning descr="Field can be converted to a local variable">l</warning> = 4;

  public Object test() {
    class Local {
      private final int x = k++;
      private final int y = l;
    }
    return new Object() {
      private final int a = i++;
      private final int b = j;
    };
  }
}