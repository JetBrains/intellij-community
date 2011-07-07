// LocalsOrMyInstanceFieldsControlFlowPolicy

class ExceptionTestCase {
   void f(boolean a, boolean b) {<caret>
        int n;
        if ((a || a) && (n = 0) >= 2) {
            n++;   //
        }
    }
}