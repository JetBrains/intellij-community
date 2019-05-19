class Super {
    String[] f2;
    String   f1;

    Super(String f1, String... f2) {
        this.f1 = f1;
        this.f2 = f2;
    }
}
class Test extends Super{

    public Test(String f1, String... f2) {
        super(f1, f2);
    }
}