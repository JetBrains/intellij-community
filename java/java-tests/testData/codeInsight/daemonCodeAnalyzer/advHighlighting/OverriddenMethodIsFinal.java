
interface ConflictWithObject {
    <error descr="'notify()' cannot override 'notify()' in 'java.lang.Object'; overridden method is final">public void notify()</error>;
}

//--override final-------------------------------------------------------------------------
class base {
 final void f() {}
}
class derived extends base {

 <error descr="'f()' cannot override 'f()' in 'base'; overridden method is final">void f()</error> {}
               
}
