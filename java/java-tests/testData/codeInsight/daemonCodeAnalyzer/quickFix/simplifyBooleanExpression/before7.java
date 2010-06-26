// "Simplify boolean expression" "true"
class X {
    void f(Object pVal, Object n) {
        if (!pVal.equals(n))
                pVal = null;
            else if (<caret>true==true)
                pVal = n;
    }
}