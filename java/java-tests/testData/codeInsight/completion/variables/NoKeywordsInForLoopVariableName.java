import java.util.ArrayList;

public class Test {
  {

    Runnable runnable = new Runnable() {
      public void run() {
        for (StringBuffer <caret> : new ArrayList<StringBuffer>()) {

        }
      }
    };
  }

}
