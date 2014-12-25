// "Create method 'test'" "false"
public class Test {
    @Attr(te<caret>st= {new String[]{""}})
    public Test() {
    }
}

@interface Attr {
}
