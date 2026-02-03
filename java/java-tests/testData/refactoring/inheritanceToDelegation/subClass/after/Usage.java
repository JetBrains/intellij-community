public class Usage {
    public void parameterUsageMethod(DelegatedBase b) {
        b.delegatedBaseMethod();
    }

    public int testMethod() {
        A a = new A();
        B b = new B();
        parameterUsageMethod(a.getMyDelegate());
        parameterUsageMethod(b.getMyDelegate());
        a.delegatedBaseMethod();
        b.delegatedBaseMethod();
        a.equals(b);
        return a.getMyDelegate().delegatedBaseField + b.getMyDelegate().delegatedBaseField;
    }

    DelegatedBase getDelegatedBase(A a) {
        return a.getMyDelegate();
    }
}
