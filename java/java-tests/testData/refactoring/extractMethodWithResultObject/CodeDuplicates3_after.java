class C {
    {
        Object[] array;

        for (int i = 0; i < array.length; i++) {
            System.out.println(array[i]);
        }


        Object[] array1;
        for (int i = 0; i < array1.length; i++) {
            System.out.println(array1[i]);
        }
    }//ins and outs
//in: PsiLocalVariable:array
//exit: SEQUENTIAL PsiForStatement
}