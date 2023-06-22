class X {
  String yieldState(Thread.State state) {
    return switch (state) {
      case NEW -> {
        System.out.println();
        yield "NEW"<caret>
      }
      case BLOCKED -> "BLOCKED";
      default -> "some";
    };
  }
}
