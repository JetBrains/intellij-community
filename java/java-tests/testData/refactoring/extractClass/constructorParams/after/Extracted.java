public class Extracted {
    private final Test test;
    String myT;

    public Extracted(Test test) {
        this.test = test;
        this.myT = test.foo();
    }

    void bar() {
        System.out.println(myT);
    }
}