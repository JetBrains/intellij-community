class Recursion {
    private static void unrecognized (final int depth) {
        if (depth >= 0) {
            Recursion.unrecognized(depth - 1);
        }
    }

    private static void recognized (final int depth) {
        if (depth >= 0) {
            recognized(depth - 1);
        }
    }
}