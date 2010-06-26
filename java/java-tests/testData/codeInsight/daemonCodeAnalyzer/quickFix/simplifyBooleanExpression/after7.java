// "Simplify boolean expression" "true"
class X {
    void f(Object pVal, Object n) {
        if (!pVal.equals(n))
                pVal = null;
            else <caret>pVal = n;
    }
}