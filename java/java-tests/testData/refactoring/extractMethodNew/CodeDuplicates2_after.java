class C {
    {
        Object[] array;

        newMethod(array);


        Object[] array1;
        for (int i = 0; i < array1.length; i++) {
            System.out.println(array[i]);
        }
    }

    private void newMethod(Object[] array) {
        for (int i = 0; i < array.length; i++) {
            System.out.println(array[i]);
        }
    }
}