// "Convert to ThreadLocal" "true"
class Main {
  private final ThreadLocal<Boolean> property;

  Main3(final boolean property) {
    this.property = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return property;
        }
    };
  }
}