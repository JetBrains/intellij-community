import java.util.List;
import java.util.function.Consumer;

class SimplifyFalse {
  private void notifyListeners(List<Consumer<Integer>> listeners) {
    if (isClosed() || liste<caret>ners.isEmpty()) {
      return;
    }
    System.out.println("123");
  }

  private boolean isClosed() {
    return false;
  }
}
