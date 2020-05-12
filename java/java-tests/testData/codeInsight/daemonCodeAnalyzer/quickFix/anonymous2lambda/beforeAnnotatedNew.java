// "Replace with lambda" "false"
import java.lang.annotation.*;

class MyTest {
    final Runnable anonymRunnable = new @A Run<caret>nable() {
        @Override
        public void run() {
            System.out.println();
        }
    };
}

@Target({ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@interface A {}