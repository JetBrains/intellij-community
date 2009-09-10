public class C {
    public void doSomething() {
        for (Iterator it = getSomeObjects().iterator(); it.hasNext();) {
            String text = (String)it.next();
            System.out.println("text = " + text);
        }
    }

    private Collection <caret>getSomeObjects() {
        final String text = "hello";
        return getSomeObjects(text);
    }

    private Collection getSomeObjects(String text) {
        final List list = new ArrayList();
        list.add(text);
        return list;
    }
}