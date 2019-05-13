import java.util.List;

class SubClass extends BaseClass<String> {
    public static void main(String[] args) {
        new SubClass().method(null);
    }

    @Override
    public void method(List list) {}
}

class BaseClass<E> implements EntityListListener<E> {
    public void method(List list) {}
}

interface EntityListListener<E> {
   public void method(List<E> list);
}
