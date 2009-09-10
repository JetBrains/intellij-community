public class Usage {
    public void parameterUsageMethod(DelegatedBase b) {
        b.delegatedBaseMethod();
    }

    public int testMethod() {
        A a = new A();
        B b = new B();
        parameterUsageMethod(a);
        parameterUsageMethod(b);
        a.delegatedBaseMethod();
        b.delegatedBaseMethod();
        a.equals(b);
        return a.delegatedBaseField + b.delegatedBaseField;
    }

    DelegatedBase getDelegatedBase(A a) {
        return a;
    }
}
