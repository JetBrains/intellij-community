public class Sample {

    public int field = lambda(() -> {
        <selection>Sample variable = new Sample();</selection>
    });

    int lambda(Runnable r) {
        return 42;
    }

}