public class Bar {
    public int baz(byte blah) {
        int <caret><selection>var</selection> = blah + 5;
        return var + 9;
    }
}
