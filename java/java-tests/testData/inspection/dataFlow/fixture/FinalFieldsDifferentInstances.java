class BrokenAlignment {

  private static boolean dominates(final WatchRequestImpl request, final WatchRequestImpl other) {
    if (request.myToWatchRecursively) {
      return other.myRootPath.startsWith(request.myRootPath);
    }

    return !other.myToWatchRecursively && <warning descr="Condition 'request.myRootPath.equals(other.myRootPath)' is always 'true' when reached">request.myRootPath.equals(other.myRootPath)</warning>;
  }

  private static class WatchRequestImpl {
    private final boolean myToWatchRecursively;
    private final String myRootPath = "";

    private WatchRequestImpl(boolean toWatchRecursively) {
      myToWatchRecursively = toWatchRecursively;
    }

  }

}