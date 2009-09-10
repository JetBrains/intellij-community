class A {
    public class <caret>Inner {
        private Inner myI;
        private Inner myI2;
    }
}

class B {
    private A.Inner b = new A.Inner();
    
}