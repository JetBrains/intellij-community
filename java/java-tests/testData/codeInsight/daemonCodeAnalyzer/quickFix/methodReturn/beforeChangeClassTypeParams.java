// "Make 'doin' return 'int'" "true"
abstract class AsyncTask<A, B, C> {
    abstract C doin(A... a);
}
class AAsync extends AsyncTask<String, Integer, Long> {
    @Override
    Long doin(String... a) {
        return <caret>1;
    }
}