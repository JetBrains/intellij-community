class Simple extends Parent {

    @Override
    protected Simple clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
}
class Parent implements Cloneable {


}