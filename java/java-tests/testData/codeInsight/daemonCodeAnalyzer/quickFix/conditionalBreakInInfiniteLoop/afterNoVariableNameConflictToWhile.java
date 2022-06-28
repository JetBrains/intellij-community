// "Move condition to loop" "true"
class Main {
  private int variableWithSameName = 1;
  public static void main(String[] args) {
    int i = 1;
      while (i < 100) {
          int variableWithAnotherName = -100;
          i = i + variableWithSameName;
      }
  }
}