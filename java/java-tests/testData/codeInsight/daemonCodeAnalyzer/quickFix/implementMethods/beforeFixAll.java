// "Apply all 'Implement methods' fixes in file" "true"
interface I {
    int foo();
}
class A<caret> implements I {
}
class B implements I {
}
class C {
    I i = new I();
}