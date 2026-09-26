// "Convert to record class" "false"
class <caret>Problem {
    private final int myLine;
    private final byte code;
    private final int myColumn;

    Problem(int line, int column, byte code) {
        this.code = code;
        myLine = line;
        myColumn = column;
    }

    static Problem make() {
        int lineArg = 0;
        byte columnArg = 42;
        byte oneMore = 2;
        return new Problem(lineArg, columnArg, oneMore);
    }
}
