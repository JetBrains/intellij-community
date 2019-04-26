// "Replace with max()" "true"

import java.util.Collection;

class Scratch {
  native int scale();

  private static Integer getMaxScale(Collection<Scratch> updated) {
    int maxScale = updated.stream().mapToInt(Scratch::scale).filter(b -> b >= 0).max().orElse(0);
      return maxScale;
  }
}