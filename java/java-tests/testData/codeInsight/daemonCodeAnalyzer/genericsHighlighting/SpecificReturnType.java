abstract class Field<D> {
    public D getValue(){return null;}
}

class LabelField extends Field<Object> {
    public Object getValue() { return null; }
}
interface MyInterface<D> {
    D getValue();
}

class MyLabelField extends LabelField implements MyInterface<Object> {
}