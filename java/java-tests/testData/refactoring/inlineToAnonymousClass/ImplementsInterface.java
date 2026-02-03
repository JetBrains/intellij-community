
class A {
    private ActionListener b = new Inner();

    private class <caret>Inner implements ActionListener {
        public void actionPerformed(int e) {
        }
    }
}
interface ActionListener {
    public void actionPerformed(int e);

}