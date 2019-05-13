// "Convert to ThreadLocal" "true"
class Main {
    private final ThreadLocal<Boolean> property = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

  Main3(boolean property) {
    this.property.set(property);
  }
}