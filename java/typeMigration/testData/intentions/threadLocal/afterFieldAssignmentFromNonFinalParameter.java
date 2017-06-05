// "Convert to ThreadLocal" "true"
class Main {
  private final ThreadLocal<Boolean> property;

  Main3(boolean property) {]
    if (property) {
      property = false;
    }
      boolean finalProperty = property;
      this.property = new ThreadLocal<Boolean>() {
          @Override
          protected Boolean initialValue() {
              return finalProperty;
          }
      };
  }
}