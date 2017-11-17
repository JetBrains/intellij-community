class WriteDifferentFieldsDuplicate {
    Runnable x;
    Runnable y;

    private void foo() {
        <selection>
        if (x != null) {
            x.run();
            x = null;
        }</selection>

        if (y != null) {
            y.run();
            y = null;
        }
    }
}