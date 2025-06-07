public class LambdaTest {
    Op lambda_fib =  (n) -> (n < 2) ? 1 : <error descr="Cannot read value of field 'lambda_fib' from inside the field's definition">lambda_fib</error>.op(n - 1) + <error descr="Cannot read value of field 'lambda_fib' from inside the field's definition">lambda_fib</error>.op(n - 2);

    {
        Op lambda_fib =  (n) -> (n < 2) ? 1 : <error descr="Variable 'lambda_fib' might not have been initialized">lambda_fib</error>.op(n - 1) + lambda_fib.op(n - 2);
    }

    interface Op {
        int op(int n);
    }
}