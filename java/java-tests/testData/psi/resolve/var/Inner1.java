public class Inner1 {
    private int a = 0;
    private class A extends B{
        {
            int j = <caret>a;
        }
    }
}

class B {
    private int a = 0;
}

