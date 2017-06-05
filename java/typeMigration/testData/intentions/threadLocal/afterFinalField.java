// "Convert to ThreadLocal" "true"
class Foo {
  private final ThreadLocal<Boolean> property;

  Foo(boolean property) {
    this.property = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return property;
        }
    };
  }
}