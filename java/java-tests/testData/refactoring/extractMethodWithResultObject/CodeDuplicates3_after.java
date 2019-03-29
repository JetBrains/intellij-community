class C {
    {
        Object[] array = { "a", 1 };

        NewMethodResult x = newMethod(array);
        for (int i = 0; i < array.length; i++) {
            System.out.println(array[i]);
        }


        Object[] array1 = { "b", 2 };
        for (int i = 0; i < array1.length; i++) {
            System.out.println(array1[i]);
        }
    }//ins and outs
//in: PsiLocalVariable:array
//exit: SEQUENTIAL PsiForStatement

    NewMethodResult newMethod(Object[] array) {
        for (int i = 0; i < array.length; i++) {
            System.out.println(array[i]);
        }
        return new NewMethodResult();
    }

    class NewMethodResult {
        public NewMethodResult() {
        }
    }
}