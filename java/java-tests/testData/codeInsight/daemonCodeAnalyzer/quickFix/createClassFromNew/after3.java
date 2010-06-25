// "Create Class 'MyInteger'" "true"
public class Test {
    public static void main() {
        int xxx = 3;
        Integer i = new MyInteger(xxx);
    }
}

public class MyInteger {
    public MyInteger(int xxx) {<caret>
    }
}