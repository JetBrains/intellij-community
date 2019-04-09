import java.util.*;

class Main {

  private final Map<Integer, Map<String, String>> myScheduledUpdates = null;

  void foo() {
    new Runnable() {
      @Override
      public void run() {
          NewMethodResult x = newMethod();
      }

        NewMethodResult newMethod() {
            myScheduledUpdates.keySet().toArray(new Object[myScheduledUpdates.keySet().size()]);
            return new NewMethodResult();
        }
    };
    myScheduledUpdates.keySet().toArray(new Object[myScheduledUpdates.keySet().size()]);
  }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}