public class StatementsBeforeSuper extends Super {

    public StatementsBeforeSuper() {
        this(<caret>hello());
        final int hello = hello();
        System.out.println(hello());
    }

    public StatementsBeforeSuper(int i) {
        super(i);
    }

    static int hello() {
        return 1;
    }
}