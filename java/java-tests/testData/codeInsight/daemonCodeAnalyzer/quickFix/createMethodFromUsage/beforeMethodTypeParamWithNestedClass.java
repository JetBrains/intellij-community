// "Create method 'f' in 'Nested" "true-preview"
public class CreateMethodTest {
  public <T> void aMethod(final T t) {
    class Nested {
        public T call() {
            T result = f<caret>(t);
            return result;
        }
    }
  }
}