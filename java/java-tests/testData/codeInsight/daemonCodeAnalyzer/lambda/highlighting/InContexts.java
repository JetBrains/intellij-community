interface I {
    void m(int x);
}
class InContexts {
    void m(I s) { }

    void assignment() {
        I s1 = (x-> { System.out.println(); });
        I s2 = ((x-> { System.out.println(); }));
    }
    
    void method() {
        m((x-> { System.out.println(); }));
        m(((x-> { System.out.println(); })));
    }

    I returnContext() {
        return (x -> {System.out.println();});
    }
}

interface I1<A, B> {
   B foo(A a);
}
class ValueLambdaInContext {

    <Z> void m1(I1<String, Z> i) {  }
    <Z> void m2(I1<String, I1<String, Z>> i) {  }

    void exprMethod() {
        m1(s1 -> 1);
        m2(s1 -> s2 -> 1);
    }

    void exprAssignment() {
        I1<String, Integer> in1 = s1 -> 1;
        I1<String, I1<String, Integer>> in2 = s1 -> s2 -> 1;
    }

    void statementMethod() {
        m1(s1 -> { return 1; });
        m2(s1 -> { return s2 -> { return 1; }; });
    }

    void statementAssignment() {
        I1<String, Integer> in1 = s1 -> { return 1; };
        I1<String, I1<String, Integer>> in2 = s1 -> { return s2 -> { return 1; }; };
    }
}
