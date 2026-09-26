// "Remove local variable 'i'" "true-preview"
import org.jetbrains.annotations.Contract;

class a {
    private void run() {
    }

  @Contract(pure = true)
  private Object dodo() {
    return null;
  }
}

