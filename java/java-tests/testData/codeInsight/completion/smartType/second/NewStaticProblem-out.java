public class Test{
    
    public class F {}
    
    public int x(F f) {
        return 1;
    }

    public static void main(String[] args) {
        Test test = new Test();
        test.x(new F())<caret>;
    }
}