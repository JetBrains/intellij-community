import static java.lang.String.valueOf;

public class RenameCollisions {
    public static class StaticInnerClass {
        public static void siMethod<caret>() {}
        public static void staticContext() {
            valueOf(0);
        }
    }
}
