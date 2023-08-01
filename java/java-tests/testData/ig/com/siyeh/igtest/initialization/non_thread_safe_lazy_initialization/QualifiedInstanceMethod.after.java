class QualifiedInstanceMethod {

    private static final class OurIsInteralHolder {
        static final Boolean ourIsInteral = getApplication().isInteral();
    }

    public boolean isInternal() {
        return OurIsInteralHolder.ourIsInteral;
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