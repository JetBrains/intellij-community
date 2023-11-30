import org.openjdk.jmh.annotations.Param;

class Test {
  @Param({"1", "5", "10"})
  public int value;
  @Param("123")
  public int value2;
  
  public void benchmark() {
    if (<warning descr="Condition 'value < 0' is always 'false'">value < 0</warning>) return;
    if (<warning descr="Condition 'value == 12' is always 'false'">value == 12</warning>) {
      throw new RuntimeException();
    }
    if (<warning descr="Condition 'value == 1 || value == 5 || value == 10' is always 'true'">value == 1 || value == 5 || <warning descr="Condition 'value == 10' is always 'true'">value == 10</warning></warning>) {
      System.out.println("ok");
    }
  }
  
  public void benchmark2() {
    if (<warning descr="Condition 'value2 == 123' is always 'true'">value2 == 123</warning>) {}
  }
}