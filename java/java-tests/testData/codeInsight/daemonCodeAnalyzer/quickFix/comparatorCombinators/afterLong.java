// "Replace with Comparator.comparingLong" "true"

import java.util.*;

public class Main {
  void sort(List<Duration> durations) {
    durations.sort(Comparator.comparingLong(d -> d.getEnd() - d.getStart()));
  }

  interface Duration {
    long getStart();
    long getEnd();
  }
}
