class Test {

    private static void f(boolean a, boolean b) {
        if (a) {
            newMethod(b);
        } else {
            System.out.println("");
        }
    }

    private static void newMethod(boolean b) {
        if (b) {
            System.out.println("");
            return;
        } else {
            System.out.println("");
            return;
        }
    }

}