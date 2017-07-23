class MyInheritor<caret> extends A {

}

abstract class A {}

class B1 extends A {}
class C11 extends B1 {}
class C12 extends B1 {}

class B2 extends A {}
class C21 extends B2 {}
class C22 extends B2 {}

class B3 extends A {}
class C31 extends B3 {}
class C32 extends B3 {}

class B4 extends A {}
class C41 extends B4 {}
class C42 extends B4 {}