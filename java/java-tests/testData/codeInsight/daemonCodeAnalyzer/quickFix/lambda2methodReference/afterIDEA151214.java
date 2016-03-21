// "Replace lambda with method reference" "true"

import java.util.concurrent.CompletableFuture;

class Test
{
    public static void main(String[] args)
    {
        CompletableFuture.completedFuture(new Foo()).thenCompose(Foo::get);
    }

    private static class Foo
    {
        public CompletableFuture<?> get()
        {
            return null;
        }
    }
}
