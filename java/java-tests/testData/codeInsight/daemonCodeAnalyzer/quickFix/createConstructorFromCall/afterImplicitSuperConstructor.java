// "Create constructor" "true-preview"
class Test extends A{

    public Test(String a) {
        <selection></selection>
    }

    public void t() {
        new Test("a"){};
    }
}

class A {
}