interface A {
   class Entry {}
}

class B implements A {
    private class Entry {}
}


class C extends B {
    static void foo(<caret>Entry e){
    }
}