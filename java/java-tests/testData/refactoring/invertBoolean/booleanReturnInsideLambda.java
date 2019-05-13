class C {
  interface I {
    boolean b();
  }

  private boolean bo<caret>ol() {
    I i = () -> {
      return true;
    };
    return false;
  }
}
