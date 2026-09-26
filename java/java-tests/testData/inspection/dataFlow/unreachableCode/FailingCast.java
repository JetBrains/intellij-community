
class Test {
  public static void main(String[] args) {
    Object o = "12";
    Integer a = (Integer) o;
    <warning descr="Unreachable code">System.out.println("1");</warning>
  }
}