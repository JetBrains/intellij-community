package a.b;
import a.*;
class Foo {
	void m(Outer o){
		Object obj = o.getData();
	}
}