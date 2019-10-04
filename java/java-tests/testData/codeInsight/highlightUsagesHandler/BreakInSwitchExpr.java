class Main {
  public static void main(String[] args) {
    String s = switch (args.length) {
      case 1:
        <caret>yield "abc";
      default:
        yield "";
    };
  }
}