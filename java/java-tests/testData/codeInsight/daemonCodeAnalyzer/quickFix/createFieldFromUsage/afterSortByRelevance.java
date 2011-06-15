// "Create Field 'field'" "true"
class A {

    private MyCloserMap field;

    void bar() {
        field.put("a", "b");
    }

}

class MyCloserMap {
    void put(String a, String b) {}
}