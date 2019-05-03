import org.jetbrains.annotations.NotNull;

import java.util.*;

class Main {

  private final Map<Integer, Map<String, String>> myScheduledUpdates = null;

  void foo() {
    new Runnable() {
      @Override
      public void run() {
        newMethod();
      }
    };
    newMethod();
  }

    @NotNull
    private Object[] newMethod() {
        return myScheduledUpdates.keySet().toArray(new Object[myScheduledUpdates.keySet().size()]);
    }
}