package foo;

public class Builder {
    private String bar;

    public Builder setBar(String bar) {
        this.bar = bar;
        return this;
    }

    public Test createTest() {
        return new Test(bar);
    }
}