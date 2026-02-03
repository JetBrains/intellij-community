class SwitchExpressionType {
  public static void main(String[] args) {
    System.out.<caret>print(switch (args.length) {
      case 1 -> 1;
      default -> 2;
    });
  }
}