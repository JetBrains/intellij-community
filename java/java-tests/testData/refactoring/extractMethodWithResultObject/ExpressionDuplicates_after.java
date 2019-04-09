import java.util.*;

class C {
    {
        Object[] o = null;
        List l = newMethod(o).expressionResult;

        List l1 = new ArrayList(Arrays.asList(new Object[0]));
    }

    NewMethodResult newMethod(Object[] o) {
        return new NewMethodResult(new ArrayList(Arrays.asList(o)));
    }

    static class NewMethodResult {
        private ArrayList expressionResult;

        public NewMethodResult(ArrayList expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}