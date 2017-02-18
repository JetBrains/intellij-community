import a.A;

@A.<error descr="'a.A.Test' has protected access in 'a.A'">Test</error> ()
class B extends A {}

@A.<error descr="'a.A.Test' has protected access in 'a.A'">Test</error> ()
class C {}