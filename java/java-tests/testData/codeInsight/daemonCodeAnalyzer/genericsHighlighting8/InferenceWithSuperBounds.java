public class ExampleProblem {
    <T> void asserting(T t, Simple<T> l){
    }

    <K> Simple<? super K> comp(K k){
        return null;
    }

    public void main(String[] args) {
        asserting(0, comp(0));
    }
}

class Simple<SST>{

}
