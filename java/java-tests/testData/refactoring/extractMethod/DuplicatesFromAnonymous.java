import java.util.*;

class Main {

  private final Map<Integer, Map<String, String>> myScheduledUpdates = null;

  void foo() {
    new Runnable() {
      @Override
      public void run() {
        <selection>myScheduledUpdates.keySet().toArray(new Object[myScheduledUpdates.keySet().size()])</selection>;
      }
    };
    myScheduledUpdates.keySet().toArray(new Object[myScheduledUpdates.keySet().size()]);
  }
}