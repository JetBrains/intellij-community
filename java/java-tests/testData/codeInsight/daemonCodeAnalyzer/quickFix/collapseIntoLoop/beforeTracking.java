// "Collapse into loop" "true"
class X {
  public int hashCode() {
    <caret>System.out.println(1);
    for (int i = 0; i < 10; i++) {}
    System.out.println(1);
    for (int i = 0; i < 10; i++) {}
    System.out.println(1);
    for (int i = 0; i < 10; i++) {}
    System.out.println(1);
    for (int i = 0; i < 10; i++) {}
  }
}