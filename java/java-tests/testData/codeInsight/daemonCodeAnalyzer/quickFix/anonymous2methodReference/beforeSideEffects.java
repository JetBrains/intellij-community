// "Replace with method reference" "false"
class Test {
  public interface I {
    String m();
  }

  public static void main(String[] args) {
    I<String> supplier = () -> new Ob<caret>ject().toString();

    System.out.println(supplier.get());
    System.out.println(supplier.get());
  }
}
