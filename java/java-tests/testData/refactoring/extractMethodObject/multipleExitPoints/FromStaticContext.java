class Bag {
    Integer x;
    Integer y;
}

class Foo {
    public static void foo() {
        Bag b = new Bag();
        System.out.println(<selection>b</selection>.x);
        System.out.println(b.x);
    }
}