import java.util.*;

class IteratePositiveCheck {
  public List<String> myLines = new ArrayList<>();

  private int getDistanceBack(final int idxStart, final List<String> lines) {
    if (idxStart < 0) return lines.size();
    int cnt = lines.size() - 1;
    for (int i = idxStart; i >= 0 && cnt >= 0; i--, cnt--) {
      if (! myLines.get(i).equals(lines.get(cnt))) return (cnt + 1);
    }
    return cnt + 1;
  }
}