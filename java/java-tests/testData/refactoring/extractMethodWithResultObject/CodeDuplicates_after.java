class C {
    {
        int i = 0;

        NewMethodResult x = newMethod(i);
        System.out.println(i);
        System.out.println(128);
    }//ins and outs
//in: PsiLocalVariable:i
//exit: SEQUENTIAL PsiExpressionStatement

    NewMethodResult newMethod(int i) {
        System.out.println(i);
        return new NewMethodResult();
    }

    class NewMethodResult {
        public NewMethodResult() {
        }
    }
}