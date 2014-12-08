// "Create method 'test'" "false"
public class Test {
    @Attr(te<caret>st= {"", 1})
    public Test() {
    }
}

@interface Attr {
}
