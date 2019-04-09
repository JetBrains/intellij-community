class C {
    {
        Object[] array = { "a", 1 };

        NewMethodResult x = newMethod(array);


        Object[] array1 = { "b", 2 };
        for (int i = 0; i < array1.length; i++) {
            System.out.println(array1[i]);
        }
    }

    NewMethodResult newMethod(Object[] array) {
        for (int i = 0; i < array.length; i++) {
            System.out.println(array[i]);
        }
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}