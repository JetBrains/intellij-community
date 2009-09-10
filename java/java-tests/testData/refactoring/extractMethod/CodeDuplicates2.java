class C {
    {
        Object[] array;

        <selection>for (int i = 0; i < array.length; i++) {
            System.out.println(array[i]);
        }</selection>


        Object[] array1;
        for (int i = 0; i < array1.length; i++) {
            System.out.println(array[i]);
        }
    }
}