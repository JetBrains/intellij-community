class TestFinal2 {
    static void foo(final boolean[] b) {
        for(boolean <warning descr="Variable 'bx' can have 'final' modifier">bx</warning> : b) {
            System.out.println("bx = " + bx);
        }
        for(boolean by : b) {
            by = false;
        }
    }
}
