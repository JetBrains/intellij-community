import org.jetbrains.annotations.NotNull;

import java.util.*;

class AssignmentInsideLamda {

  private final List<Comparator<String>> comparators = new ArrayList<>();

  public void test(String[] args) {
    Arrays.sort(args, newMethod());
  }

    private @NotNull Comparator<String> newMethod() {
        return (o1, o2) -> {
            int result = compareForNull(o1, o2);
            if (result == 0) {
                for (Comparator<String> comparator : comparators) {
                    result = comparator.compare(o1, o2);
                    if (result != 0) {
                        return result;
                    }
                }
            }
            return result;
        };
    }

    private int compareForNull(String o1, String o2) {
    return 0;
  }
}