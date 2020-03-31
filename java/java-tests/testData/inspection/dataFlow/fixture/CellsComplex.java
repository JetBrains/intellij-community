class Foo {
  // TODO: make not complex
  public static int[] <weak_warning descr="Method 'cells' is complex: data flow results could be imprecise">cells</weak_warning>(int[] start, int[] end) {
    int overlap = 0;
    int gaps = 0;
    for (int i = 0, j = 0; j < end.length; ) {
      if (i < start.length && start[i] < end[j]) {
        overlap++;
        i++;
      } else {
        j++;
        overlap--;
      }
      if (overlap == 0) {
        gaps++;
      }
    }
    int[] cells = new int[gaps * 2];
    overlap = 0;
    gaps = 0;
    int previousOverlap = 0;
    for (int i = 0, j = 0; j < end.length; ) {
      if (i < start.length && start[i] < end[j]) {
        overlap++;
        if (previousOverlap == 0) {
          cells[gaps++] = start[i];
        }
        i++;
      } else {
        overlap--;
        if (overlap == 0) {
          cells[gaps++] = end[j];
        }
        j++;
      }
      previousOverlap = overlap;
    }

    return cells;
  }

}