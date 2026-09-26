public class StatementsBeforeSuper extends Super {

    private static int hello;

    public StatementsBeforeSuper() {
        hello = hello();
        this(hello);
        final int hello = StatementsBeforeSuper.hello;
        System.out.println(StatementsBeforeSuper.hello);
    }

    public StatementsBeforeSuper(int i) {
        super(i);
    }

    static int hello() {
        return 1;
    }
}