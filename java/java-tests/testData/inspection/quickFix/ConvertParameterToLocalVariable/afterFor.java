// "Convert to local variable" "true"
class Temp {
  public Temp() {
    for (int i = 0; i < 10; i++) {
        <caret>int p = i;
      System.out.print(p);
    }
  }
}