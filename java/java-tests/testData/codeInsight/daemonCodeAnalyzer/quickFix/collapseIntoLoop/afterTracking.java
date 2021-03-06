// "Collapse into loop" "true"
class X {
  public int hashCode() {
      for (int j = 0; j < 4; j++) {
          System.out.println(1);
          for (int i = 0; i < 10; i++) {}
      }
  }
}