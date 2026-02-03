// "Create method 'fooBar'" "true"
import java.util.function.Consumer;

class MyTest {
  public void testMethod() {
    overloadOtherParam("anything", this::fooBar);
  }

    private void fooBar(String s) {
        <caret>
    }


    private void overloadOtherParam(String anything, Consumer<String> consumer) {
  }

  private void overloadOtherParam(int anything, Consumer<String> consumer) {
  }

}
