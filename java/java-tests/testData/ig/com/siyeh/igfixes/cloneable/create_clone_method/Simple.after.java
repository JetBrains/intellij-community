class Simple implements Cloneable {


    @Override
    public Simple clone() {
        try {
            return (Simple) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}