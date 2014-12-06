// "Create method 'test'" "true"
public class Test {
    @Attr(test= "")
    public Test() {
    }
}

@interface Attr {
    String test();
}
