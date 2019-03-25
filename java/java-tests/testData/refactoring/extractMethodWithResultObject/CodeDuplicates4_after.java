class C {
    {
        Object[] array;

        for (int i = 0; i < array.length; i++) {
            System.out.println(array[i]);
        }


        Object[] array1;
        for (int j = 0; j < array1.length; j++) {
            System.out.println(array1[j]);
        }
    }//ins and outs
//in: PsiLocalVariable:array
//exit: SEQUENTIAL PsiForStatement

    public NewMethodResult newMethod(Object[] array) {
        return new NewMethodResult();
    }

    public class NewMethodResult {
    }
}