public class PrivateSuperMethod {
  private static class Base {
    @Override
    public int hashCode() {
      return 37;
    }
  }

  private static class Inner extends Base {
    private void run() {
      <caret>int marker = 0;
    }

    @Override
    public int hashCode() {
      return 42;
    }
  }
}
