class C {
    {
        int[] a = new int[1];
        int x = a[1];
        x = 42;
        System.out.println(x);
    }
}