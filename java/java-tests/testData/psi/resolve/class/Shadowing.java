interface A {
   class Entry {}
}

class B implements A {
    private class Entry {}
}


class C extends B {
    static void foo(<ref>Entry e){
    }
}