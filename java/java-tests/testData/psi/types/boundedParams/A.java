import test.*;

class A<T extends Collection> {
    void method(T param) {
	Iterator it = param.iterator();
    }
}
