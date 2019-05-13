class WriteDifferentFieldsDuplicate {
    Runnable x;
    Runnable y;

    private void foo() {

        newMethod();

        if (y != null) {
            y.run();
            y = null;
        }
    }

    private void newMethod() {
        if (x != null) {
            x.run();
            x = null;
        }
    }
}