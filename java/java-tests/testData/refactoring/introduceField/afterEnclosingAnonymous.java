class TestRefactoring {
    public final Integer toField;

    public TestRefactoring() {
        toField = new Integer("0");
        Runnable r = new Runnable() {
            @Override
            public void run() {
                System.out.println(toField);
            }
        };
    }
}