import test.*;

class A {
    public void method() {
	List list = new List();
        list.add(new A());
        list.iterator();
    }
}
