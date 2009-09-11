public class RenameCollisions {
    public static final int <caret>STATIC_FIELD = 5;

    public static class StaticInnerClass {
        public static final int SI_STATIC_FIELD = 9;
        public static void staticContext(int param) {
            int var1 = SI_STATIC_FIELD + STATIC_FIELD;
        }
    }

    private class InnerClass {
        public static final int INNER_STATIC_FIELD = 13;
        public void instanceContext(int param) {
            int var1 = INNER_STATIC_FIELD + STATIC_FIELD;
        }
    }
}
