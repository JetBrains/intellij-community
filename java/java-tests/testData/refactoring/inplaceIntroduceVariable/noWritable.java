class C {
    {
        int[] a = new int[1];
        a[1] = 42;
        System.out.println(a<caret>[1]);
    }
}