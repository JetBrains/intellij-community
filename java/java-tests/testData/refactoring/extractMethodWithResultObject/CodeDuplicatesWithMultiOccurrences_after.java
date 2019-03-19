class Test {
    void t(java.util.Map<String, String> m) {
        String f = "";
        System.out.println("f = " + f + ", " + m.get(f));
    }//ins and outs
//in: PsiLocalVariable:f
//exit: EXPRESSION PsiReferenceExpression:f
}