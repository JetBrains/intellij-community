class Test {
  interface F {
    String get(Test t);
  }

    Wrapper foo() {
    return new Wrapper("");
  }
  
  {
    F f = test -> test.foo().getValue();
  }

    public class Wrapper {
        private final String value;

        public Wrapper(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}