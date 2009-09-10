public class Foo {
    String getComponent(Integer i) { return null; }
    Integer myI;

    public void usage() {
        if (myI != null)
            method(myI);
    }

    void me<caret>thod(Integer i) {
        System.out.println(getComponent(myI) + getComponent(i));
    }

}