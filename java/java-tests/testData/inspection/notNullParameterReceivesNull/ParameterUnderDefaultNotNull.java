import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
class FromDemo {
  <T extends Number> T get(T <warning descr="Parameter annotated @NotNullByDefault should not receive 'null' as an argument">b</warning>) {
    return null;
  }

  public void test() {
    Object o = new FromDemo().get(null);
    if (o == null) {

      System.out.println("1");
    }
  }

  <T extends Number> T get2(T <warning descr="Parameter annotated @NotNullByDefault should not receive 'null' as an argument">o</warning>) {
    return null;
  }

  public void test2() {
    Object o = new FromDemo().get2(null);
    if (o == null) {

      System.out.println("1");
    }
  }
}