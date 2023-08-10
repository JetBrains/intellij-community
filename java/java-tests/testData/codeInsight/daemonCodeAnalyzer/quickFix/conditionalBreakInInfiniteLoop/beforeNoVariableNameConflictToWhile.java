// "Move condition to loop" "true-preview"
class Main {
  private int variableWithSameName = 1;
  public static void main(String[] args) {
    int i = 1;
    while<caret>(true) {
      if (i >= 100) {
        break;
      } else {
        int variableWithAnotherName = -100;
      }
      i = i + variableWithSameName;
    }
  }
}