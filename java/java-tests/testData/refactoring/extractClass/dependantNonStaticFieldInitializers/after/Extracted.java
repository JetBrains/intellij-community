public class Extracted {
    private final Test test;
    int[] myT;

    public Extracted(Test test) {
        this.test = test;
        this.myT = new int[]{test.getIi()};
    }
}