abstract class DemoInvertBoolean {

    boolean b;

    DemoInvertBoolean(boolean <caret>b) {
        this.b = b;
    }

    DemoInvertBoolean() {
        this(true);//this true will be inverted
    }

    abstract void f1();

    public static void main(String[] args) {
        DemoInvertBoolean demo = new DemoInvertBoolean(true) {//this true will not be inverted
            @Override
            public void f1() {

            }
        };
    }
}