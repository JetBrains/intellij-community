public class RefactorBug {
    public static void bar(int a, float b) {
    }

    /**
     * @see #bar(int, float)
     */
    public static void somethingElse() {

    }
}
