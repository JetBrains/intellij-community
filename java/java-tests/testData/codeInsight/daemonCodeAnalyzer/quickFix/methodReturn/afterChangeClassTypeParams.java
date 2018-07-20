// "Make 'doin' return 'int'" "true"
abstract class AsyncTask<A, B, C> {
    abstract C doin(A... a);
}
class AAsync extends AsyncTask<String, Integer, Integer> {
    @Override
    Integer doin(String... a) {
        return 1;
    }
}