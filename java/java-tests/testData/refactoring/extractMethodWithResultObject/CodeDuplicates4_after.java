class C {
    {
        Object[] array = { "a", 1 };

        for (int i = 0; i < array.length; i++) {
            System.out.println(array[i]);
        }


        Object[] array1 = { "b", 2 };
        for (int j = 0; j < array1.length; j++) {
            System.out.println(array1[j]);
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