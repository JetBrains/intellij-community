// "Replace with max()" "true"

import java.util.Collection;

class Scratch {
  native int scale();

  private static Integer getMaxScale(Collection<Scratch> updated) {
    int maxScale = 0;
    f<caret>or (Scratch b : updated) {
      if (maxScale < b.scale()) {
        maxScale = b.scale();
      }
    }
    return maxScale;
  }
}