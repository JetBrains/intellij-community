// "Move condition to loop" "false"
class Main {
  private int variableWithSameName = 1;
  public static void main(String[] args) {
    int i = 1;
    while<caret>(true) {
      if (i < 100) {
        break;
      } else {
        int variableWithSameName = -100;
      }
      i = i + variableWithSameName;
    }
  }
}