// "Convert to a record" "true"
class A {
    public static final class <caret>Nested { // convert to the record
        private final int j;

        public Nested(int j) {
          this.j = j;
        }
    }
}