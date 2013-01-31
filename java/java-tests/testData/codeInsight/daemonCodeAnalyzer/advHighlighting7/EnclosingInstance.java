class OtherClass {
    public class InnerClass {}
}

class Main<B extends OtherClass.InnerClass> { }
class Main1 extends <error descr="No enclosing instance of type 'OtherClass' is in scope">OtherClass.InnerClass</error> { }

class NonDefaultConstructorContainer {
    public class Inner {
        public Inner(String s) {}
    }
}

class UsageWithParenthesis extends NonDefaultConstructorContainer.Inner {
    public UsageWithParenthesis() {
       (new NonDefaultConstructorContainer()).super("");
    }

    public UsageWithParenthesis(NonDefaultConstructorContainer e) {
       (e).super("");
    }
}
