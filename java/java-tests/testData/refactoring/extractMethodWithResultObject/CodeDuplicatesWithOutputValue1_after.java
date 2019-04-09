import java.util.*;

class C {
    {
        Object[] array = { "a", 1 };

        NewMethodResult x = newMethod(array);
        List l1 = x.l1;

        List l2 = null;
        l2 = new ArrayList(Arrays.asList(getObjects()));

        System.out.println("l1 = " + l1 + ", l2 = " + l2);
    }

    NewMethodResult newMethod(Object[] array) {
        List l1 = null;
        l1 = new ArrayList(Arrays.asList(array));
        return new NewMethodResult(l1);
    }

    static class NewMethodResult {
        private List l1;

        public NewMethodResult(List l1) {
            this.l1 = l1;
        }
    }


    String[] getObjects() { return new String[1]; }
}