class ExplicitTypeArguments {
  {
    boolean b = a<caret>();
  }

  private <T> T a() {
    return null;
  }
}