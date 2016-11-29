import javax.annotation.concurrent.GuardedBy;

class Example
{
    private final Distribution distribution = new Distribution();

    public void add(long value)
    {
        synchronized (distribution) {
            distribution.total += value;
            <error descr="Cannot resolve symbol 'total'">total</error> += value;
        }

    }

    protected static class Distribution
    {
        @GuardedBy("this")
        private long total = 0;
    }
}
