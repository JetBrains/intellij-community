import java.util.*;

class C {
    {
        Object[] array;

        List l1 = null;
        l1 = new ArrayList(Arrays.asList(array));

        List l2 = null;
        l2 = new ArrayList(Arrays.asList(getObjects()));

        System.out.println("l1 = " + l1 + ", l2 = " + l2);
    }//ins and outs
//in: PsiLocalVariable:array
//out: PsiLocalVariable:l1
//exit: SEQUENTIAL PsiExpressionStatement

    public NewMethodResult newMethod(Object[] array) {
        List l1 = null;
        l1 = new ArrayList(Arrays.asList(array));
        return new NewMethodResult(l1);
    }

    public class NewMethodResult {
        private List l1;

        public NewMethodResult(List l1) {
            this.l1 = l1;
        }
    }


    String[] getObjects() {
    }
}