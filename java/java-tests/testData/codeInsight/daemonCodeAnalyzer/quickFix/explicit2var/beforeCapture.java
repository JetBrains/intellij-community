// "Replace explicit type with 'var'" "false"
import java.util.List;

class Program {
    void getRidOfType() {
        Li<caret>st<?> list = getList(getClass());
        list = getList(Integer.class);
    }

    public static <T> List<T> getList(Class<T> clazz) {
        return null;
    }
}