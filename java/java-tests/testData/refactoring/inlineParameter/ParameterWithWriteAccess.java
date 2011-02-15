class Clazz {
    public Clazz(String b<caret>ar) {
        bar = bar + bar;
        System.out.println(bar);
    }

    public static void main(String[] args) {
        new Clazz("bar");
    }
}