// "Bring 'Resource r' into scope" "false"
class IDEA121153 {
  String foo()
  {
    try (Resource r = getResource()) {
    }
    return <caret>r.get();
  }

  public Resource getResource() {
    return new Resource();
  }

  class Resource implements AutoCloseable{

    @Override
    public void close() {

    }

    public String get() {
      return "";
    }
  }
}