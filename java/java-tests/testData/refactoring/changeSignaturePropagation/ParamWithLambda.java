class ChangeSignatureTest {
     private static void level1() {
        Runnable runnable = () -> level2();
        Runnable runnable1 = () -> { level2();};
        level2();
    }

    private static void le<caret>vel2() {

    }
}