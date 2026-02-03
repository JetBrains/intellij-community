class ExplicitTypeArguments {
  {
    boolean b = this.<Boolean>a().booleanValue();
  }

  private <T> T a() {
    return null;
  }
}