public class Builder {
    private int i = 2;
    private int j;

    public Builder setI(int i) {
        this.i = i;
        return this;
    }

    public Builder setJ(int j) {
        this.j = j;
        return this;
    }

    public Test createTest() {
        return new Test(i, j);
    }
}