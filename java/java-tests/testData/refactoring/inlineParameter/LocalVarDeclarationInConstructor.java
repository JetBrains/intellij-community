class Base {
    public Base() {
    }
}

class Inheritor extends Base {
    public Inheritor(String b<caret>ar) {
        super();
        System.out.println(bar);
    }

    public static void main(String[] args) {
        new Inheritor("bar".toString());
    }
}