class C {
  interface I {
    boolean b();
  }

  private boolean boolInverted() {
    I i = () -> {
      return true;
    };
    return true;
  }
}
