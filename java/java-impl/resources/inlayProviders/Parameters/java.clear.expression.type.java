class Test {
  public static final int i = new Random().nextInt(3);

  public static void main(String[] args) {
    System.out.println(switch (i) {
      case 1:
        yield "Apples";
      case 2:
        yield "Bananas";
      default:
        yield "Apples and bananas";
    });

    System.out.println(Math.random() > 0.5 ? "Apples" : "Bananas");
  }
}