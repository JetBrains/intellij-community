public class Demo {

    static class MyParent {
        private final String value;

        MyParent(String value) {
            this.value = value;
        }
    }

    static class MyC<caret>hild extends MyParent {
        MyChild(String value) {
            super(value);
        }
    }

    public static void main(String[] args) {

        String value = "something";
        final MyParent p;
        if (true)
            p = new MyChild(value);
        else
            p = new MyParent("value");
    }
}