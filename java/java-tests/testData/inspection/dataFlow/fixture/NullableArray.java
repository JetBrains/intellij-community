import org.jetbrains.annotations.*;

class Test {
  @NotNull
  public static int[] add(@Nullable final int[] ints, final int from, final int to) {
    if (ints == null || ints.length == 0)
      return new int[]{from, to};

    for (int i = 0, j = 1; j < ints.length; i+=2, j+=2) {
      final int intStart = ints[i];
      final int intFinish = ints[j];

      //check contained
      if (intStart <= from && to <= intFinish)
        return ints;

      //try expand 'to' bound
      if (intStart <= from && from <= intFinish) {
        ints[j] = to;
        return ints;
      }

      //try expand 'from' bound
      if (intStart <= to && to <= intFinish) {
        ints[i] = from;
        return ints;
      }

      //if we add an interval that contains ors interval
      //may produce duplicates
      if (from <= intStart && intFinish <= to) {
        ints[i] = from;
        ints[j] = to;
        return ints;
      }

      if (from == intFinish + 1) {
        ints[j] = to;
        return ints;
      }
      if (to == intStart - 1) {
        ints[i] = from;
        return ints;
      }
    }

    //TODO: insert interval sorted?
    final int[] newInts = new int[ints.length + 2];
    System.arraycopy(ints, 0, newInts, 0, ints.length);
    newInts[ints.length] = from;
    newInts[ints.length+1] = to;
    return newInts;
  }}