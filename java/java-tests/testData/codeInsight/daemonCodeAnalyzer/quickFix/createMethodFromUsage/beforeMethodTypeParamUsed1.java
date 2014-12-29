// "Create method 'f'" "true"
public class CreateMethodTest {
  public <T> T aMethod(T t) {
    T result = f<caret>(t);
    return result;
  }
}