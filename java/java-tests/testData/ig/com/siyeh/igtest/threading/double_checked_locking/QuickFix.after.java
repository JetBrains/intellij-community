class QuickFix {

    private static final class InstanceHolder {
        private static final Object INSTANCE = new Object();
    }

    public static Object getInstance() {
        return InstanceHolder.INSTANCE;
  }
}