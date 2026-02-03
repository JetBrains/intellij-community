import static java.lang.String.CASE_INSENSITIVE_ORDER;

public class RenameCollisions {
    public static class StaticInnerClass {
        public static final int SI_STATIC_FIELD<caret> = 9;
        public static void staticContext() {
            CASE_INSENSITIVE_ORDER.getClass();
        }
    }
}
