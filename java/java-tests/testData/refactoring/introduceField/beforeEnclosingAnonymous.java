class TestRefactoring {
    public TestRefactoring() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                Integer to<caret>Field = new Integer("0");
                System.out.println(toField);
            }
        };
    }
}