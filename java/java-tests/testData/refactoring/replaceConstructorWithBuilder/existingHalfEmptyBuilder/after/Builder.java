public class Builder {
    private int[] i;
    private int j;

    public Builder setI(int... i) {
        this.i = i;
        return this;
    }

    public Test createTest() {
        return new Test(j, i);
    }

    public Builder setJ(int j) {
        this.j = j;
        return this;
    }
}