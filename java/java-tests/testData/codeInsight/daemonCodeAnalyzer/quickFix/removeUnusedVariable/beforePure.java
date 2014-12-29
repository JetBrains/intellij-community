// "Remove variable 'i'" "true"
import org.jetbrains.annotations.Contract;

class a {
    private void run() {
        Object <caret>i = dodo();
    }

  @Contract(pure = true)
  private Object dodo() {
    return null;
  }
}

