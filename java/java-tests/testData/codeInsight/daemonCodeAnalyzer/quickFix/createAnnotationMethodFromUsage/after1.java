// "Create method 'test'" "true-preview"
public class Test {
    @Attr(test= "")
    public Test() {
    }
}

@interface Attr {
    String test();
}
