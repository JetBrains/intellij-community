import static java.lang.String.valueOf;

public class RenameCollisions {
    public static class StaticInnerClass {
        public static final int SI_STATIC_FIELD<caret> = 9;
        public static void staticContext() {
            valueOf(null);
        }
    }
}
