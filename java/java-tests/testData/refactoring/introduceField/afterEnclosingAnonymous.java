class TestRefactoring {
    public final Integer integer;

    public TestRefactoring() {
        integer = new Integer("0");
        Runnable r = new Runnable() {
            @Override
            public void run() {
                System.out.println(integer);
            }
        };
    }
}