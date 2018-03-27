// "Convert to local" "true"
class Temp {
  public Temp(int <caret>p) {
    for (int i = 0; i < 10; i++) {
      p = i;
      System.out.print(p);
    }
  }
}