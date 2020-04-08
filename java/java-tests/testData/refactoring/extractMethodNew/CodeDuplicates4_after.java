class C {
    {
        Object[] array;

        newMethod(array);


        Object[] array1;
        newMethod(array1);
    }

    private void newMethod(Object[] array) {
        for (int i = 0; i < array.length; i++) {
            System.out.println(array[i]);
        }
    }
}