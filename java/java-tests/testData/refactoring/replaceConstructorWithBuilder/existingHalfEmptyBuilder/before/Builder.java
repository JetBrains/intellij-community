public class Builder {
    private int[] i;

    public Builder setI(int... i) {
        this.i = i;
        return this;
    }

    public Test createTest() {
        return new Test(j, i);
    }
}