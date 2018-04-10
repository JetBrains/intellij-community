// "Convert to local" "true"
import java.util.ArrayList;

class ITest {

  private ArrayList<String> may<caret>BeLocal = new ArrayList<String>(); //my comment to keep in code

  public IntelliJBugConvertToLocal(int x, int z) {

    if (x == 5) {
      mayBeLocal.add("jjj");
    }

    if (x > z) {
      useIt(mayBeLocal);
    }
  }
  @SuppressWarnings("UnusedParameters")
  private void useIt(Object data) {
    System.out.println(data);
  }
}