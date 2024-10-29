class C {
    {
        int[] a = new int[1];
        a[1] = 42;
        int x = a[1];
        System.out.println(x);
    }
}