class Test {

    public void test(boolean b) {
        int a = 1;
        if (true) {
            System.out.println(a);
        } else {
            NewMethodResult x = newMethod(a);
            System.out.println(a);
        }
    }//ins and outs
//in: PsiLocalVariable:a
//exit: SEQUENTIAL PsiMethod:test

    NewMethodResult newMethod(int a) {
        System.out.println(a);
        return new NewMethodResult();
    }

    class NewMethodResult {
        public NewMethodResult() {
        }
    }


}