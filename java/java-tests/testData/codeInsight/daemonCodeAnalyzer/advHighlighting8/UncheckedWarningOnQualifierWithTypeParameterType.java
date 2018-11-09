abstract class Data<D extends Data<D,T>, T> {

    private static <D extends Data<D,?>> double displayValue(D data) {
        return data.getValue();
    }

    private final double value;

    protected Data(T complement, double value) {
        this.value = value;
    }


    public double getValue() {
        return value;
    }

}