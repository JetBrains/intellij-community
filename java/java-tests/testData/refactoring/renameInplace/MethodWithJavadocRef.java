public class RefactorBug {
    public static void som<caret>ething(int a, float b) {
    }

    /**
     * @see #something(int, float)
     */
    public static void somethingElse() {

    }
}
