// "Move condition to loop" "false"
class Main {
  public static void main(String[] args) {
    while<caret> (true) {
      if (!outputParser.processMessageLine(callback)) {
        break;
      }
    }
  }
}