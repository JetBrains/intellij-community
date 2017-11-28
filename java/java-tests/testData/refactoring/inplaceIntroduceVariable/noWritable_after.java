class C {
    {
        int[] a = new int[1];
        a[1] = 42;
        int i = a[1];
        System.out.println(i);
    }
}