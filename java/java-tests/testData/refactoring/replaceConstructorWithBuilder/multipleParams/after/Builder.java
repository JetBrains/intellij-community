public class Builder {
    private int j;
    private int[] i;

    public Builder setJ(int j) {
        this.j = j;
        return this;
    }

    public Builder setI(int... i) {
        this.i = i;
        return this;
    }

    public Test createTest() {
        return new Test(j, i);
    }
}