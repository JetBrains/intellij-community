// "Create method 'f' in 'Nested" "true-preview"
public class CreateMethodTest {
  public <T> void aMethod(final T t) {
    class Nested {
        public T call() {
            T result = f(t);
            return result;
        }

        private T f(T t) {
            <selection>return null;</selection>
        }
    }
  }
}