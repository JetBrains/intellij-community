class Test {
    {
        int x = 0, y;
        <selection>y = ++x;</selection>
        System.out.println("x = " + x);
        System.out.println("y = " + y);
    }
}