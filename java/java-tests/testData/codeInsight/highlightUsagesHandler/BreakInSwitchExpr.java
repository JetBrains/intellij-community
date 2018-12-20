class Main {
  public static void main(String[] args) {
    String s = switch (args.length) {
      case 1:
        br<caret>eak "abc";
      default:
        break "";
    };
  }
}