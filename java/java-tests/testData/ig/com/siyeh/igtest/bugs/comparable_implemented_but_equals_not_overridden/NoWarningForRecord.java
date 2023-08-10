record NoWarningForRecord(int x, int y) implements Comparable<NoWarningForRecord> {
  @Override
  public int compareTo(NoWarningForRecord o) {
    return (x - o.x) + (y - o.y);
  }
}