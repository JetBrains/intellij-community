package a.b;
import a.*;
public class Foo {
	private <T> T f() {	return null;}
	void m(Outer o){
		o.get(f<error descr="'a.Outer.Inner' is inaccessible from here">()</error>);
    o.get1();
    o.f = f<error descr="'a.Outer.Inner' is inaccessible from here">()</error>;
	}
}