package samePackage;

public class C1 {
    protected C1(int i) { } //protected constructor
}
class Cc extends C1 {
    public Cc() {
        super(9);
    }

    public void amethod() {
        new C1(9); // <<<<---------- This is an error
    }
}
