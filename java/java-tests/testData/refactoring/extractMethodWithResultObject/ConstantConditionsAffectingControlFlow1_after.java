class Test {

    public void test(boolean b) {
        int a = 1;
        if (true) {
            System.out.println(a);
        } else {
            System.out.println(a);
        }
    }//ins and outs
//in: PsiLocalVariable:a
//exit: SEQUENTIAL PsiMethod:test


}