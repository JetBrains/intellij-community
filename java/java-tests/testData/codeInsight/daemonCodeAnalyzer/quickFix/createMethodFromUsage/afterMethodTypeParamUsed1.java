// "Create method 'f'" "true-preview"
public class CreateMethodTest {
  public <T> T aMethod(T t) {
    T result = f(t);
    return result;
  }

    private <T> T f(T t) {
        <selection>return null;</selection>
    }
}