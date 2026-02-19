import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class Test<T> {

  private final ArrayList<T> createdItems = new ArrayList<>();
  private final ArrayList<T> modifiedItems = new ArrayList<>();
  private final ArrayList<T> removedItems = new ArrayList<>();

  @Override
  public String toString() {
    return getClass().getSimpleName() +
            getString();
  }

    private @NotNull String getString() {
        return " Created: " + createdItems.size() +
                " Modified: " + modifiedItems.size() +
                " Removed: " + removedItems.size();
    }
}