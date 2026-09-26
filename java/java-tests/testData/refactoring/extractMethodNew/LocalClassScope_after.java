public class Sample {

    public int field = lambda(() -> {
        newMethod();
    });

    private void newMethod() {
        Sample variable = new Sample();
    }

    int lambda(Runnable r) {
        return 42;
    }

}