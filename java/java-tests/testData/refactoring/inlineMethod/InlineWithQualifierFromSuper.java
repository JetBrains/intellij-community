class Base {
    String id;
    public String getID() {
        return id;
    }
}

class Derived extends Base {
    String <caret>getName() {
        return getID();
    }
    
    static void usage(Derived element) {
        StringBuffer buffer = new StringBuffer();
        buffer.add(element.getName());
    }
}