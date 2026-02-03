public class Builder {
    private int i = 2;

    public Builder setI(int i) {
        this.i = i;
        return this;
    }

    public Test createTest() {
        return new Test(i);
    }
}