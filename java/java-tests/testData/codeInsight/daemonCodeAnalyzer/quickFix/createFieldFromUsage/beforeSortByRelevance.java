// "Create Field 'field'" "true"
class A {

    void bar() {
        f<caret>ield.put("a", "b");
    }

}

class MyCloserMap {
    void put(String a, String b) {}
}