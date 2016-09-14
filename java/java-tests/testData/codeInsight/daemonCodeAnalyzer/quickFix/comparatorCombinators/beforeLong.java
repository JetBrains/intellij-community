// "Replace with Comparator.comparingLong" "true"

import java.util.*;

public class Main {
  void sort(List<Duration> durations) {
    durations.sort((d1, d2) -> Long.com<caret>pare(d1.getEnd()-d1.getStart(), d2.getEnd()-d2.getStart()));
  }

  interface Duration {
    long getStart();
    long getEnd();
  }
}
