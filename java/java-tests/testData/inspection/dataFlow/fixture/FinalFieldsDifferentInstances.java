class BrokenAlignment {

  private static boolean dominates(final WatchRequestImpl request, final WatchRequestImpl other) {
    if (request.myToWatchRecursively) {
      return other.myRootPath.startsWith(request.myRootPath);
    }

    return !other.myToWatchRecursively && request.myRootPath.equals(other.myRootPath);
  }

  private static class WatchRequestImpl {
    private final boolean myToWatchRecursively;
    private final String myRootPath = "";

    private WatchRequestImpl(boolean toWatchRecursively) {
      myToWatchRecursively = toWatchRecursively;
    }

  }

}