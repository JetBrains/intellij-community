class QualifiedInstanceMethod {

  private static Boolean ourIsInteral = null;

  public boolean isInternal() {
    if (ourIsInteral == null) {
      <warning descr="Lazy initialization of 'static' field 'ourIsInteral' is not thread-safe"><caret>ourIsInteral</warning> = getApplication().isInteral();
    }
    return ourIsInteral;
  }

  private static Application getApplication() {
    return new Application();
  }

  static class Application {
    public boolean isInteral() {
      return false;
    }
  }
}