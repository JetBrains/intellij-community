// "Convert to local" "true"
import java.util.ArrayList;

class ITest {

    public IntelliJBugConvertToLocal(int x, int z) {

        //my comment to keep in code
        ArrayList<String> mayBeLocal = new ArrayList<String>();
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