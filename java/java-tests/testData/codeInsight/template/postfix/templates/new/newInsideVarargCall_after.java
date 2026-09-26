import java.util.Arrays;
class Bug { }
public class NewPostfixVsVararg {
  public static void main(String[] args) {
    testVararg(
      new Bug()<caret>
    );
  }
  private static void testVararg(Bug... bugs) {
    System.out.println("Arrays.toString(bugs) = " + Arrays.toString(bugs));
  }
}