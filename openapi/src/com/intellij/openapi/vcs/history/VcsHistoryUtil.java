package com.intellij.openapi.vcs.history;

public class VcsHistoryUtil {
  public static int compare(VcsFileRevision first, VcsFileRevision second) {
    if (first instanceof CurrentRevision && second instanceof CurrentRevision) {
      return compareNumbers(first, second);
    }
    if (second instanceof CurrentRevision) return -1 * compare(second, first);

    if (first instanceof CurrentRevision) {
      int result = compareNumbers(first, second);
      if (result == 0) {
        return 1;
      }
      else {
        return result;
      }
    }
    else {
      return compareNumbers(first, second);
    }
  }

  private static int compareNumbers(VcsFileRevision first, VcsFileRevision second) {
    return first.getRevisionNumber().compareTo(second.getRevisionNumber());
  }
}
