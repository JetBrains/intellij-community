import java.util.List;

public class Zoo2 {
    <T> void assertThat(T t, List<T> tt) { }
    <T> List<T> wrap(T t) { }

    public void main(String[] args) {
        assertThat(args, wrap(args)<caret>);
    }

}


