// "Pull method 'foo' to 'Int'" "true"
public class Test implements Int {
    @Override
    public void foo(){}
}

interface Int {
    void foo();
}
