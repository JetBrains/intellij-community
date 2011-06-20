abstract class DemoInvertBoolean {

    boolean b;

    DemoInvertBoolean(boolean <caret>bInverted) {
        this.b = !bInverted;
    }

    DemoInvertBoolean() {
        this(false);//this true will be inverted
    }

    abstract void f1();

    public static void main(String[] args) {
        DemoInvertBoolean demo = new DemoInvertBoolean(false) {//this true will not be inverted
            @Override
            public void f1() {

            }
        };
    }
}